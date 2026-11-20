package tn.voltpark.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** session-service injoignable : la gateway doit repondre 503 avec un ProblemDetail. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // port 1 : aucune application n'ecoute, la connexion est refusee
        properties = "SESSION_SERVICE_URL=http://127.0.0.1:1")
class GatewayFallbackTest {

    @Autowired
    private WebTestClient client;

    @Test
    void returnsProblemDetailWhenSessionServiceIsDown() {
        client.get().uri("/api/v1/stations")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:voltpark:problem:service-unavailable")
                .jsonPath("$.title").isEqualTo("Service indisponible")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.detail").isNotEmpty()
                .jsonPath("$.instance").isEqualTo("/api/v1/stations")
                .jsonPath("$.timestamp").isNotEmpty();
    }
}
