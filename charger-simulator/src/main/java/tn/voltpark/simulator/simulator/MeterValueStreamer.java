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

import java.time.Duration;
import java.time.Instant;
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

    /** Une mesure par seconde reelle. */
    private static final long READING_INTERVAL_MS = 1000;

    @GrpcClient("energy-service")
    private EnergyServiceGrpc.EnergyServiceStub energyStub;

    /**
     * Envoie une mesure par seconde jusqu'a la demande d'arret ou jusqu'a maxSeconds,
     * puis ferme le flux : energy-service cloture alors la session.
     * En cas d'erreur UNAVAILABLE, Resilience4j relance la methode avec un delai croissant
     * (voir application.yml), puis appelle skipSession.
     */
    @Retry(name = "energyService", fallbackMethod = "skipSession")
    public void streamSession(SimulatedSession session, int maxSeconds) {
        CompletableFuture<StreamSummary> result = new CompletableFuture<>();
        StreamObserver<MeterValue> requests = energyStub
                .withDeadlineAfter(maxSeconds + DEADLINE_MARGIN_SECONDS, TimeUnit.SECONDS)
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

        log.info("Starting simulated session {} on charger {}", session.sessionId(), session.chargerId());
        Instant end = session.startedAt().plusSeconds(maxSeconds);

        try {
            while (!session.isStopRequested() && !result.isDone() && Instant.now().isBefore(end)) {
                requests.onNext(session.profile().next(Instant.now()));
                session.awaitStop(Math.min(READING_INTERVAL_MS, remainingMillis(end)));
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
            log.warn("Stream for session {} failed: {}", session.sessionId(), cause.getMessage());
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (TimeoutException e) {
            throw Status.DEADLINE_EXCEEDED.withDescription("no summary received").asRuntimeException();
        }
    }

    private static long remainingMillis(Instant end) {
        return Math.max(0, Duration.between(Instant.now(), end).toMillis());
    }

    /** Appelee quand les essais sont epuises ou que l'erreur n'est pas reessayable. */
    @SuppressWarnings("unused")
    private void skipSession(SimulatedSession session, int maxSeconds, Throwable cause) {
        String reason = cause instanceof StatusRuntimeException e ? e.getStatus().toString() : cause.toString();
        log.error("Simulated session {} skipped: {}", session.sessionId(), reason);
    }
}
