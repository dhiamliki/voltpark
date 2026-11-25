package tn.voltpark.session.domain.service;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.voltpark.energy.grpc.v1.SessionProgress;
import tn.voltpark.session.api.dto.LiveProgressResponse;
import tn.voltpark.session.api.error.ResourceNotFoundException;
import tn.voltpark.session.client.grpc.EnergyGrpcClient;
import tn.voltpark.session.repository.ChargingSessionRepository;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pont entre WatchSession (gRPC server streaming) et un flux Server-Sent Events.
 *
 * Evenements envoyes :
 *   progress  a chaque nouvelle mesure (LiveProgressResponse)
 *   end       la session est cloturee dans energy-service, le flux se termine
 *   error     pas encore de mesure (NOT_FOUND) ou energy-service indisponible, le flux se termine
 *
 * Si le client HTTP se deconnecte, l'appel gRPC est annule.
 */
@Service
public class SessionLiveService {

    private static final Logger log = LoggerFactory.getLogger(SessionLiveService.class);

    private final ChargingSessionRepository sessionRepository;
    private final EnergyGrpcClient energyGrpcClient;
    private final long timeoutMs;

    public SessionLiveService(ChargingSessionRepository sessionRepository,
                              EnergyGrpcClient energyGrpcClient,
                              @Value("${voltpark.live.timeout-ms:900000}") long timeoutMs) {
        this.sessionRepository = sessionRepository;
        this.energyGrpcClient = energyGrpcClient;
        this.timeoutMs = timeoutMs;
    }

    public SseEmitter open(String sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Session de charge", sessionId);
        }

        SseEmitter emitter = new SseEmitter(timeoutMs);
        AtomicReference<Runnable> cancel = new AtomicReference<>(() -> { });

        // deconnexion, delai depasse ou erreur d'ecriture : l'appel gRPC est annule
        emitter.onCompletion(() -> cancel.get().run());
        emitter.onTimeout(() -> {
            cancel.get().run();
            emitter.complete();
        });
        emitter.onError(e -> cancel.get().run());

        cancel.set(energyGrpcClient.watchSession(sessionId, timeoutMs, new StreamObserver<>() {
            @Override
            public void onNext(SessionProgress progress) {
                try {
                    emitter.send(SseEmitter.event().name("progress")
                            .data(LiveProgressResponse.from(progress), MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    // client HTTP parti : on arrete l'appel gRPC
                    log.debug("Live view of session {} closed by client", sessionId);
                    cancel.get().run();
                }
            }

            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                if (status.getCode() == Status.Code.CANCELLED) {
                    emitter.complete();
                    return;
                }
                String message = status.getCode() == Status.Code.NOT_FOUND
                        ? "Aucune mesure recue pour la session " + sessionId
                        : "energy-service indisponible (" + status.getCode() + ")";
                log.warn("Live view of session {} ended: {}", sessionId, status);
                sendQuietly(emitter, "error", Map.of("sessionId", sessionId, "message", message));
                emitter.complete();
            }

            @Override
            public void onCompleted() {
                sendQuietly(emitter, "end", Map.of("sessionId", sessionId));
                emitter.complete();
            }
        }));
        return emitter;
    }

    private static void sendQuietly(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // client deja parti
        }
    }
}
