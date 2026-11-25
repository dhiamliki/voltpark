package tn.voltpark.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.util.function.Tuple2;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route du suivi en direct (SSE). Le delai de reponse global est reduit a 1 s pour ce test :
 * le flux, dont les evenements arrivent toutes les 1,5 s, doit quand meme passer en entier,
 * evenement par evenement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "GATEWAY_RESPONSE_TIMEOUT=1s")
class GatewayLiveRouteTest {

    private static final MockWebServer sessionService = new MockWebServer();
    private static final String EVENT = "event: progress\ndata: {\"energyKwh\":1.0}\n\n";
    private static final long GAP_MS = 1500;

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
    void resetQueue() {
        sessionService.setDispatcher(new QueueDispatcher());
    }

    /** Trois evenements, un toutes les 1,5 s : plus que le delai global de 1 s. */
    private static MockResponse slowEventStream() {
        return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .setBody(EVENT.repeat(3))
                .throttleBody(EVENT.length(), GAP_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    void streamsLiveEventsWithoutGlobalTimeoutAndWithoutBuffering() {
        sessionService.enqueue(slowEventStream());

        List<Tuple2<Long, String>> events = client.mutate().responseTimeout(Duration.ofSeconds(20)).build()
                .get().uri("/api/v1/sessions/abc/live")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .elapsed()
                .collectList()
                .block(Duration.ofSeconds(20));

        assertThat(events).hasSize(3);
        assertThat(events).allSatisfy(event -> assertThat(event.getT2()).contains("energyKwh"));
        // pas de mise en tampon : le premier evenement arrive avant que le flux soit termine
        assertThat(events.get(0).getT1()).isLessThan(GAP_MS);
        assertThat(events.get(1).getT1() + events.get(2).getT1()).isGreaterThan(2 * GAP_MS - 500);
    }

    @Test
    void doesNotRetryLiveStream() {
        int before = sessionService.getRequestCount();
        sessionService.enqueue(new MockResponse().setResponseCode(503));
        sessionService.enqueue(new MockResponse().setBody("should not be reached"));

        client.get().uri("/api/v1/sessions/abc/live")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(sessionService.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void otherRoutesKeepTheGlobalTimeout() {
        sessionService.enqueue(new MockResponse()
                .setBody("late")
                .setHeadersDelay(GAP_MS, TimeUnit.MILLISECONDS));

        // la route normale abandonne apres 1 s (fallback du circuit breaker, 503)
        client.get().uri("/api/v1/sessions/abc")
                .exchange()
                .expectStatus().isEqualTo(503);
    }
}
