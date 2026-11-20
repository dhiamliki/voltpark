package tn.voltpark.energy.grpc;

import io.grpc.Context;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.GetSessionEnergyRequest;
import tn.voltpark.energy.grpc.v1.MeterValue;
import tn.voltpark.energy.grpc.v1.SessionProgress;
import tn.voltpark.energy.grpc.v1.StreamSummary;
import tn.voltpark.energy.grpc.v1.WatchSessionRequest;
import tn.voltpark.energy.model.SessionEnergy;
import tn.voltpark.energy.service.EnergyService;
import tn.voltpark.energy.service.InvalidReadingException;
import tn.voltpark.energy.service.SessionProgressView;

import java.time.Duration;

/**
 * Implementation gRPC de EnergyService (energy.proto v1).
 * Les exceptions levees ici sont converties en codes de statut par GrpcExceptionAdvice.
 */
@GrpcService
public class EnergyGrpcService extends EnergyServiceGrpc.EnergyServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(EnergyGrpcService.class);

    private final EnergyService energyService;
    private final Duration watchPollInterval;

    public EnergyGrpcService(EnergyService energyService,
                             @Value("${voltpark.watch.poll-interval:1s}") Duration watchPollInterval) {
        this.energyService = energyService;
        this.watchPollInterval = watchPollInterval;
    }

    /**
     * Client streaming : chaque mesure est enregistree des sa reception.
     * A la fin du flux, la session est cloturee et un resume est renvoye.
     */
    @Override
    public StreamObserver<MeterValue> streamMeterValues(StreamObserver<StreamSummary> responseObserver) {
        return new StreamObserver<>() {

            private String sessionId;
            private long readingsReceived;
            private double lastEnergyKwh;
            private boolean failed;

            @Override
            public void onNext(MeterValue value) {
                if (failed) {
                    return;
                }
                try {
                    if (sessionId == null) {
                        sessionId = value.getSessionId();
                    } else if (!sessionId.equals(value.getSessionId())) {
                        throw new InvalidReadingException("a stream must carry a single session_id");
                    }
                    SessionEnergy session = energyService.recordReading(ProtoMapper.toNewReading(value));
                    readingsReceived++;
                    lastEnergyKwh = session.getLastEnergyKwh();
                } catch (RuntimeException e) {
                    // l'appel est termine par GrpcExceptionAdvice, on ignore la suite du flux
                    failed = true;
                    throw e;
                }
            }

            @Override
            public void onError(Throwable t) {
                // le client a coupe le flux : la session reste ouverte, il pourra la reprendre
                log.info("Meter stream for session {} interrupted by client: {}", sessionId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                if (failed) {
                    return;
                }
                if (sessionId != null) {
                    energyService.closeSession(sessionId);
                }
                responseObserver.onNext(StreamSummary.newBuilder()
                        .setSessionId(sessionId == null ? "" : sessionId)
                        .setReadingsReceived(readingsReceived)
                        .setLastEnergyKwh(lastEnergyKwh)
                        .build());
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Server streaming : envoie l'etat de la session a chaque nouvelle mesure.
     *
     * Le service ne garde aucune liste d'abonnes en memoire : il relit la base de donnees
     * a intervalle regulier (1 s par defaut). Le service reste ainsi sans etat, ce qui est
     * une exigence du projet : n'importe quelle instance peut servir un client, et un
     * redemarrage ne perd aucune information.
     *
     * La boucle s'arrete quand la session est cloturee ou quand le client annule l'appel.
     */
    @Override
    public void watchSession(WatchSessionRequest request, StreamObserver<SessionProgress> responseObserver) {
        ServerCallStreamObserver<SessionProgress> observer =
                (ServerCallStreamObserver<SessionProgress>) responseObserver;
        String sessionId = request.getSessionId();

        // leve SessionNotFoundException (NOT_FOUND) si la session est inconnue
        SessionProgressView last = null;
        while (!isCancelled(observer)) {
            SessionProgressView progress = energyService.getLatestProgress(sessionId);
            if (!progress.equals(last)) {
                observer.onNext(ProtoMapper.toProto(progress));
                last = progress;
            }
            if (progress.closed()) {
                observer.onCompleted();
                return;
            }
            try {
                Thread.sleep(watchPollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.debug("WatchSession for {} cancelled by client", sessionId);
    }

    /** Unary : bilan energetique d'une session, utilise pour la facturation. */
    @Override
    public void getSessionEnergy(GetSessionEnergyRequest request,
                                 StreamObserver<tn.voltpark.energy.grpc.v1.SessionEnergy> responseObserver) {
        SessionEnergy session = energyService.getSessionEnergy(request.getSessionId());
        responseObserver.onNext(ProtoMapper.toProto(session));
        responseObserver.onCompleted();
    }

    /**
     * isCancelled() de l'observateur n'est mis a jour qu'apres le retour de la methode,
     * car gRPC traite les evenements d'un appel un par un. Le Context, lui, est annule
     * immediatement : on verifie donc les deux.
     */
    private static boolean isCancelled(ServerCallStreamObserver<?> observer) {
        return observer.isCancelled() || Context.current().isCancelled();
    }
}
