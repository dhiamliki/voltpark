package tn.voltpark.charger.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Previent le simulateur de bornes qu'une transaction demarre ou s'arrete, pour qu'il envoie
 * (ou arrete d'envoyer) les mesures de la session a energy-service.
 * Une erreur est seulement journalisee : elle ne doit jamais changer la reponse SOAP.
 */
@Component
public class SimulatorClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatorClient.class);

    private final RestClient restClient;

    public SimulatorClient(@Value("${voltpark.simulator.url}") String baseUrl,
                           @Value("${voltpark.simulator.connect-timeout-ms}") int connectTimeoutMs,
                           @Value("${voltpark.simulator.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public void startSimulation(String sessionId, String chargerId) {
        try {
            restClient.post().uri("/api/simulations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("sessionId", sessionId, "chargerId", chargerId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Simulator started session {} on charger {}", sessionId, chargerId);
        } catch (Exception e) {
            log.warn("Could not start simulation of session {} on charger {}: {}", sessionId, chargerId, e.getMessage());
        }
    }

    public void stopSimulation(String sessionId) {
        try {
            restClient.delete().uri("/api/simulations/{sessionId}", sessionId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Simulator stopped session {}", sessionId);
        } catch (Exception e) {
            log.warn("Could not stop simulation of session {}: {}", sessionId, e.getMessage());
        }
    }
}
