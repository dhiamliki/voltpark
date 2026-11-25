package tn.voltpark.simulator.simulator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.voltpark.simulator.config.SimulatorProperties;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Mode planifie (SIMULATOR_ENABLED=true) : lance une session aleatoire au demarrage, puis
 * toutes les SIMULATOR_INTERVAL_SECONDS secondes. Ces sessions n'existent pas dans session-service.
 */
@Component
@ConditionalOnProperty(prefix = "simulator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimulationScheduler {

    private final MeterValueStreamer streamer;
    private final SimulatorProperties properties;
    private final Random random = new Random();

    public SimulationScheduler(MeterValueStreamer streamer, SimulatorProperties properties) {
        this.streamer = streamer;
        this.properties = properties;
    }

    @Scheduled(initialDelay = 0, fixedRateString = "${simulator.interval-seconds}", timeUnit = TimeUnit.SECONDS)
    public void simulateSession() {
        SimulatedSession session = new SimulatedSession(
                "SIM-" + System.currentTimeMillis(), properties.chargerId(), properties.timeFactor(), random);
        streamer.streamSession(session, properties.durationSeconds());
    }
}
