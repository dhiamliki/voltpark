package tn.voltpark.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** La gateway devant un faux session-service (MockWebServer). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final MockWebServer sessionService = new MockWebServer();

    static {
        try {
            sessionService.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void sessionServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("SESSION_SERVICE_URL", () -> sessionService.url("/").toString());
    }

    @AfterAll
    static void stopServer() throws IOException {
        sessionService.shutdown();
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void resetQueue() throws InterruptedException {
        // file de reponses vide pour chaque test
        sessionService.setDispatcher(new QueueDispatcher());
        // oublie les requetes recues pendant les tests precedents
        while (sessionService.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // rien
        }
    }

    @Test
    void forwardsApiRequestsToSessionService() throws InterruptedException {
        int before = sessionService.getRequestCount();
        sessionService.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
                .setBody("pong"));

        String requestId = client.get().uri("/api/v1/ping")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody(String.class).isEqualTo("pong")
                .returnResult().getResponseHeaders().getFirst("X-Request-Id");

        RecordedRequest forwarded = sessionService.takeRequest(1, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/ping");
        // le meme identifiant est transmis au service en aval
        assertThat(forwarded.getHeader("X-Request-Id")).isEqualTo(requestId);
        assertThat(sessionService.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void keepsIncomingRequestId() throws InterruptedException {
        sessionService.enqueue(new MockResponse().setBody("pong"));

        client.get().uri("/api/v1/ping")
                .header("X-Request-Id", "abc-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "abc-123");

        RecordedRequest forwarded = sessionService.takeRequest(1, TimeUnit.SECONDS);
        assertThat(forwarded.getHeader("X-Request-Id")).isEqualTo("abc-123");
    }

    @Test
    void answersCorsPreflightFromFrontend() {
        int before = sessionService.getRequestCount();

        client.options().uri("/api/v1/reservations")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        methods -> assertThat(methods).contains("POST", "PATCH", "DELETE"))
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

        // la requete preflight est traitee par la gateway, sans appeler session-service
        assertThat(sessionService.getRequestCount() - before).isZero();
    }

    @Test
    void rejectsCorsPreflightFromUnknownOrigin() {
        client.options().uri("/api/v1/reservations")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void exposesLocationHeaderToFrontend() {
        sessionService.enqueue(new MockResponse().setResponseCode(201).setHeader("Location", "/api/v1/reservations/1"));

        client.post().uri("/api/v1/reservations")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        exposed -> assertThat(exposed).contains("Location"));
    }

    @Test
    void retriesGetOnServiceUnavailable() {
        int before = sessionService.getRequestCount();
        sessionService.enqueue(new MockResponse().setResponseCode(503));
        sessionService.enqueue(new MockResponse().setBody("ok"));

        client.get().uri("/api/v1/stations")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");

        assertThat(sessionService.getRequestCount() - before).isEqualTo(2);
    }

    @Test
    void doesNotRetryPost() {
        int before = sessionService.getRequestCount();
        sessionService.enqueue(new MockResponse().setResponseCode(503));
        sessionService.enqueue(new MockResponse().setBody("should not be reached"));

        client.post().uri("/api/v1/reservations")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(sessionService.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void doesNotRetryOtherServerErrors() {
        int before = sessionService.getRequestCount();
        sessionService.enqueue(new MockResponse().setResponseCode(500));
        sessionService.enqueue(new MockResponse().setBody("should not be reached"));

        client.get().uri("/api/v1/stations")
                .exchange()
                .expectStatus().isEqualTo(500);

        assertThat(sessionService.getRequestCount() - before).isEqualTo(1);
    }
}
