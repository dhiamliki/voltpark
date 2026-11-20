package tn.voltpark.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Les routes sont consultables sur actuator, mais ne peuvent pas etre modifiees. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayActuatorTest {

    @Autowired
    private WebTestClient client;

    @Test
    void listsRoutes() {
        client.get().uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.route_id == 'session-api')]").exists()
                .jsonPath("$[?(@.route_id == 'session-docs')]").exists();
    }

    @Test
    void refusesRouteChanges() {
        client.post().uri("/actuator/gateway/routes/evil")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"uri\": \"http://evil.example\", \"predicates\": []}")
                .exchange()
                .expectStatus().isEqualTo(405);

        client.delete().uri("/actuator/gateway/routes/session-api")
                .exchange()
                .expectStatus().isEqualTo(405);

        client.post().uri("/actuator/gateway/refresh")
                .exchange()
                .expectStatus().isEqualTo(405);
    }
}
