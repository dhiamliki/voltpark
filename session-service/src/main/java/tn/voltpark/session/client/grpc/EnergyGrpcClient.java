package tn.voltpark.session.client.grpc;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.GetSessionEnergyRequest;
import tn.voltpark.energy.grpc.v1.SessionEnergy;
import tn.voltpark.energy.grpc.v1.SessionProgress;
import tn.voltpark.energy.grpc.v1.WatchSessionRequest;
import tn.voltpark.session.api.error.EnergyDataNotFoundException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EnergyGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(EnergyGrpcClient.class);
    private static final RetryableEnergyError RETRYABLE = new RetryableEnergyError();

    @GrpcClient("energy-service")
    private EnergyServiceGrpc.EnergyServiceBlockingStub energyStub;

    @GrpcClient("energy-service")
    private EnergyServiceGrpc.EnergyServiceStub energyAsyncStub;

    @Value("${voltpark.clients.energy.deadline-ms:2000}")
    private long deadlineMs;

    /**
     * Bilan energetique d'une session (unary GetSessionEnergy), avec une deadline sur chaque appel.
     * <ul>
     *   <li>UNAVAILABLE / DEADLINE_EXCEEDED : nouvel essai avec backoff (retry energyService),
     *       puis fallback qui renvoie null (energie indisponible) ;</li>
     *   <li>circuit energyService ouvert : fallback immediat, null ;</li>
     *   <li>NOT_FOUND : aucune mesure pour cette session, EnergyDataNotFoundException (404),
     *       sans nouvel essai et sans compter comme une panne pour le circuit.</li>
     * </ul>
     * Le fallback est sur @Retry (aspect exterieur) : place sur @CircuitBreaker (aspect interieur),
     * il renverrait un resultat avant que Retry puisse refaire l'appel.
     */
    @Retry(name = "energyService", fallbackMethod = "energyUnavailable")
    @CircuitBreaker(name = "energyService")
    public SessionEnergy getSessionEnergy(String sessionId) {
        log.info("Querying energy-service gRPC for session {}", sessionId);
        GetSessionEnergyRequest request = GetSessionEnergyRequest.newBuilder()
                .setSessionId(sessionId)
                .build();
        try {
            return energyStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .getSessionEnergy(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new EnergyDataNotFoundException(sessionId);
            }
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private SessionEnergy energyUnavailable(String sessionId, StatusRuntimeException e) {
        if (!RETRYABLE.test(e)) {
            // INVALID_ARGUMENT, INTERNAL... : ce n'est pas une indisponibilite, l'erreur remonte
            throw e;
        }
        log.warn("energy-service unavailable for session {}: {}", sessionId, e.getStatus());
        return null;
    }

    @SuppressWarnings("unused")
    private SessionEnergy energyUnavailable(String sessionId, CallNotPermittedException e) {
        log.warn("energy-service circuit open, skipping energy fetch for session {}", sessionId);
        return null;
    }

    /**
     * Suivi en direct (server streaming WatchSession, stub asynchrone).
     *
     * @return action qui annule l'appel gRPC (client HTTP deconnecte) ; sans effet si l'appel est fini
     */
    public Runnable watchSession(String sessionId, long timeoutMs, StreamObserver<SessionProgress> observer) {
        AtomicReference<ClientCallStreamObserver<WatchSessionRequest>> call = new AtomicReference<>();
        energyAsyncStub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS).watchSession(
                WatchSessionRequest.newBuilder().setSessionId(sessionId).build(),
                new ClientResponseObserver<WatchSessionRequest, SessionProgress>() {
                    @Override
                    public void beforeStart(ClientCallStreamObserver<WatchSessionRequest> requestStream) {
                        call.set(requestStream);
                    }

                    @Override
                    public void onNext(SessionProgress progress) {
                        observer.onNext(progress);
                    }

                    @Override
                    public void onError(Throwable t) {
                        observer.onError(t);
                    }

                    @Override
                    public void onCompleted() {
                        observer.onCompleted();
                    }
                });
        return () -> {
            ClientCallStreamObserver<WatchSessionRequest> requestStream = call.get();
            if (requestStream != null) {
                requestStream.cancel("live view closed", null);
            }
        };
    }
}
