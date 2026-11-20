package tn.voltpark.simulator.simulator;

import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.MeterValue;
import tn.voltpark.energy.grpc.v1.StreamSummary;
import tn.voltpark.simulator.config.SimulatorProperties;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Envoie les mesures d'une session simulee a energy-service (StreamMeterValues, client streaming). */
@Component
public class MeterValueStreamer {

    private static final Logger log = LoggerFactory.getLogger(MeterValueStreamer.class);

    /** Marge ajoutee a la duree de la session pour la deadline de l'appel. */
    private static final int DEADLINE_MARGIN_SECONDS = 15;

    private final SimulatorProperties properties;
    private final Random random = new Random();

    @GrpcClient("energy-service")
    private EnergyServiceGrpc.EnergyServiceStub energyStub;

    public MeterValueStreamer(SimulatorProperties properties) {
        this.properties = properties;
    }

    /**
     * Simule une session complete. En cas d'erreur UNAVAILABLE, Resilience4j relance
     * la methode avec un delai croissant (voir application.yml), puis appelle skipSession.
     */
    @Retry(name = "energyService", fallbackMethod = "skipSession")
    public void streamSession(String sessionId) {
        CompletableFuture<StreamSummary> result = new CompletableFuture<>();
        StreamObserver<MeterValue> requests = energyStub
                .withDeadlineAfter(properties.durationSeconds() + DEADLINE_MARGIN_SECONDS, TimeUnit.SECONDS)
                .streamMeterValues(new StreamObserver<>() {
                    @Override
                    public void onNext(StreamSummary summary) {
                        result.complete(summary);
                    }

                    @Override
                    public void onError(Throwable t) {
                        result.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });

        ChargingProfile profile = new ChargingProfile(
                sessionId, properties.chargerId(), properties.timeFactor(), random);
        log.info("Starting simulated session {} on charger {}", sessionId, properties.chargerId());

        try {
            for (int second = 0; second < properties.durationSeconds() && !result.isDone(); second++) {
                requests.onNext(profile.next(Instant.now()));
                Thread.sleep(1000);
            }
            requests.onCompleted();
            StreamSummary summary = result.get(DEADLINE_MARGIN_SECONDS, TimeUnit.SECONDS);
            log.info("Session {} finished: {} readings received, {} kWh",
                    summary.getSessionId(), summary.getReadingsReceived(), summary.getLastEnergyKwh());
        } catch (InterruptedException e) {
            requests.onError(Status.CANCELLED.asRuntimeException());
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("Stream for session {} failed: {}", sessionId, cause.getMessage());
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (TimeoutException e) {
            throw Status.DEADLINE_EXCEEDED.withDescription("no summary received").asRuntimeException();
        }
    }

    /** Appelee quand les essais sont epuises ou que l'erreur n'est pas reessayable. */
    @SuppressWarnings("unused")
    private void skipSession(String sessionId, Throwable cause) {
        String reason = cause instanceof StatusRuntimeException e ? e.getStatus().toString() : cause.toString();
        log.error("Simulated session {} skipped: {}", sessionId, reason);
    }
}
