package tn.voltpark.simulator.simulator;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.voltpark.simulator.config.SimulatorProperties;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sessions demarrees a la demande (par charger-gateway-service via l'API REST).
 * Chaque session a son propre flux gRPC, dans son propre thread : plusieurs sessions
 * peuvent tourner en parallele.
 */
@Component
public class SimulationManager {

    private static final Logger log = LoggerFactory.getLogger(SimulationManager.class);

    /** Duree maximale d'attente de la fin du flux quand on arrete une session. */
    private static final long STOP_WAIT_MS = 2000;

    private final MeterValueStreamer streamer;
    private final SimulatorProperties properties;
    private final Random random = new Random();
    private final Map<String, SimulatedSession> running = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "simulation");
        thread.setDaemon(true);
        return thread;
    });

    public SimulationManager(MeterValueStreamer streamer, SimulatorProperties properties) {
        this.streamer = streamer;
        this.properties = properties;
    }

    /** Demarre l'envoi des mesures pour cette session. */
    public SimulatedSession start(String sessionId, String chargerId) {
        SimulatedSession session = new SimulatedSession(sessionId, chargerId, properties.timeFactor(), random);
        if (running.putIfAbsent(sessionId, session) != null) {
            throw new SimulationAlreadyRunningException(sessionId);
        }
        executor.submit(() -> {
            try {
                streamer.streamSession(session, properties.maxSessionSeconds());
            } catch (RuntimeException e) {
                log.error("Simulation {} ended with an error: {}", sessionId, e.getMessage());
            } finally {
                running.remove(sessionId, session);
                session.markFinished();
            }
        });
        return session;
    }

    /**
     * Arrete la session : le flux est ferme et energy-service cloture la session.
     * Attend la fin du flux (2 s au plus) pour que le bilan soit definitif au retour.
     */
    public void stop(String sessionId) {
        SimulatedSession session = running.get(sessionId);
        if (session == null) {
            throw new SimulationNotFoundException(sessionId);
        }
        session.requestStop();
        if (!session.awaitFinished(STOP_WAIT_MS)) {
            log.warn("Simulation {} did not finish within {} ms", sessionId, STOP_WAIT_MS);
        }
    }

    public Collection<SimulatedSession> running() {
        return List.copyOf(running.values());
    }

    @PreDestroy
    void stopAll() {
        running.values().forEach(SimulatedSession::requestStop);
        executor.shutdown();
    }
}
