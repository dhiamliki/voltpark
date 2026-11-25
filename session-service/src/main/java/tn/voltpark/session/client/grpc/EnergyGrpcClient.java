package tn.voltpark.session.client.grpc;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.GetSessionEnergyRequest;
import tn.voltpark.energy.grpc.v1.SessionEnergy;
import tn.voltpark.session.api.error.EnergyDataNotFoundException;

import java.util.concurrent.TimeUnit;

@Service
public class EnergyGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(EnergyGrpcClient.class);

    @GrpcClient("energy-service")
    private EnergyServiceGrpc.EnergyServiceBlockingStub energyStub;

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
        log.warn("energy-service unavailable for session {}: {}", sessionId, e.getStatus());
        return null;
    }

    @SuppressWarnings("unused")
    private SessionEnergy energyUnavailable(String sessionId, CallNotPermittedException e) {
        log.warn("energy-service circuit open, skipping energy fetch for session {}", sessionId);
        return null;
    }
}
