package tn.voltpark.simulator.simulator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Lance une session simulee au demarrage, puis toutes les SIMULATOR_INTERVAL_SECONDS secondes. */
@Component
@ConditionalOnProperty(prefix = "simulator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimulationScheduler {

    private final MeterValueStreamer streamer;

    public SimulationScheduler(MeterValueStreamer streamer) {
        this.streamer = streamer;
    }

    @Scheduled(initialDelay = 0, fixedRateString = "${simulator.interval-seconds}", timeUnit = TimeUnit.SECONDS)
    public void simulateSession() {
        streamer.streamSession("SIM-" + System.currentTimeMillis());
    }
}
