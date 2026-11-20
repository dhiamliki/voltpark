package tn.voltpark.energy.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.GetSessionEnergyRequest;
import tn.voltpark.energy.grpc.v1.MeterValue;
import tn.voltpark.energy.grpc.v1.SessionEnergy;
import tn.voltpark.energy.grpc.v1.SessionProgress;
import tn.voltpark.energy.grpc.v1.StreamSummary;
import tn.voltpark.energy.grpc.v1.WatchSessionRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Demarre le serveur gRPC en memoire (in-process, voir application-test.yml)
 * avec une base H2, et l'appelle comme le ferait un vrai client.
 */
@SpringBootTest
@ActiveProfiles("test")
class EnergyGrpcIntegrationTest {

    private ManagedChannel channel;
    private EnergyServiceGrpc.EnergyServiceStub asyncStub;
    private EnergyServiceGrpc.EnergyServiceBlockingStub blockingStub;

    @BeforeEach
    void openChannel() {
        channel = InProcessChannelBuilder.forName("energy-test").build();
        asyncStub = EnergyServiceGrpc.newStub(channel);
        blockingStub = EnergyServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void closeChannel() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void streamThenGetSessionEnergy() throws Exception {
        String sessionId = newSessionId();

        StreamSummary summary = stream(
                meterValue(sessionId, "2026-03-10T10:00:00Z", 11.0, 0.5, 40),
                meterValue(sessionId, "2026-03-10T10:00:01Z", 11.2, 1.0, 41),
                meterValue(sessionId, "2026-03-10T10:00:02Z", 11.1, 1.6, 42));

        assertThat(summary.getSessionId()).isEqualTo(sessionId);
        assertThat(summary.getReadingsReceived()).isEqualTo(3);
        assertThat(summary.getLastEnergyKwh()).isCloseTo(1.6, within(1e-9));

        SessionEnergy energy = blockingStub.getSessionEnergy(
                GetSessionEnergyRequest.newBuilder().setSessionId(sessionId).build());

        assertThat(energy.getTotalEnergyKwh()).isCloseTo(1.6, within(1e-9));
        assertThat(energy.getReadingsCount()).isEqualTo(3);
        assertThat(energy.getPeakKwh() + energy.getOffPeakKwh()).isCloseTo(1.6, within(1e-9));
        assertThat(energy.getStartedAt().getSeconds())
                .isEqualTo(Instant.parse("2026-03-10T10:00:00Z").getEpochSecond());
    }

    @Test
    void unknownSessionReturnsNotFound() {
        GetSessionEnergyRequest request = GetSessionEnergyRequest.newBuilder().setSessionId("UNKNOWN").build();

        assertThatThrownBy(() -> blockingStub.getSessionEnergy(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void decreasingEnergyInStreamReturnsInvalidArgument() {
        String sessionId = newSessionId();

        assertThatThrownBy(() -> stream(
                meterValue(sessionId, "2026-03-10T10:00:00Z", 11.0, 2.0, 40),
                meterValue(sessionId, "2026-03-10T10:00:01Z", 11.0, 1.0, 41)))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void watchSessionEmitsProgress() throws Exception {
        String sessionId = newSessionId();
        stream(
                meterValue(sessionId, "2026-03-10T10:00:00Z", 11.0, 0.5, 40),
                meterValue(sessionId, "2026-03-10T10:01:00Z", 11.0, 0.7, 42));

        // la session est cloturee : le flux envoie le dernier etat puis se termine
        Iterator<SessionProgress> progress = blockingStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .watchSession(WatchSessionRequest.newBuilder().setSessionId(sessionId).build());
        List<SessionProgress> received = new ArrayList<>();
        progress.forEachRemaining(received::add);

        assertThat(received).isNotEmpty();
        SessionProgress latest = received.get(received.size() - 1);
        assertThat(latest.getSocPercent()).isEqualTo(42);
        assertThat(latest.getEnergyKwh()).isCloseTo(0.7, within(1e-9));
        assertThat(latest.getEstimatedMinutesRemaining()).isEqualTo(29);
    }

    @Test
    void watchUnknownSessionReturnsNotFound() {
        Iterator<SessionProgress> progress = blockingStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .watchSession(WatchSessionRequest.newBuilder().setSessionId("UNKNOWN").build());

        assertThatThrownBy(progress::hasNext)
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    /** Envoie les mesures dans un flux client et attend le resume du serveur. */
    private StreamSummary stream(MeterValue... values) throws Exception {
        CompletableFuture<StreamSummary> result = new CompletableFuture<>();
        StreamObserver<MeterValue> requests = asyncStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .streamMeterValues(new StreamObserver<>() {
                    @Override
                    public void onNext(StreamSummary value) {
                        result.complete(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        result.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
        for (MeterValue value : values) {
            requests.onNext(value);
        }
        requests.onCompleted();
        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }

    private static MeterValue meterValue(String sessionId, String time, double powerKw, double energyKwh, int soc) {
        return MeterValue.newBuilder()
                .setSessionId(sessionId)
                .setChargerId("CP-TEST")
                .setTimestamp(ProtoMapper.toTimestamp(Instant.parse(time)))
                .setPowerKw(powerKw)
                .setEnergyKwh(energyKwh)
                .setSocPercent(soc)
                .build();
    }

    private static String newSessionId() {
        return "TEST-" + UUID.randomUUID();
    }
}
