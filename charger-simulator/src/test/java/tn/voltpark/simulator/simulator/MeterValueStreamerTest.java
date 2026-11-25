package tn.voltpark.simulator.simulator;

import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.MeterValue;
import tn.voltpark.energy.grpc.v1.StreamSummary;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Une session demarree a la demande envoie ses mesures a un faux energy-service, puis ferme le flux a l'arret. */
@SpringBootTest(properties = {
        "simulator.enabled=false",
        "grpc.client.energy-service.address=in-process:fake-energy"
})
class MeterValueStreamerTest {

    private static final List<MeterValue> received = new CopyOnWriteArrayList<>();
    private static final CountDownLatch completed = new CountDownLatch(1);
    private static final Server server;

    static {
        try {
            server = InProcessServerBuilder.forName("fake-energy").directExecutor()
                    .addService(new EnergyServiceGrpc.EnergyServiceImplBase() {
                        @Override
                        public StreamObserver<MeterValue> streamMeterValues(StreamObserver<StreamSummary> response) {
                            return new StreamObserver<>() {
                                @Override
                                public void onNext(MeterValue value) {
                                    received.add(value);
                                }

                                @Override
                                public void onError(Throwable t) {
                                }

                                @Override
                                public void onCompleted() {
                                    response.onNext(StreamSummary.newBuilder()
                                            .setSessionId(received.get(0).getSessionId())
                                            .setReadingsReceived(received.size())
                                            .build());
                                    response.onCompleted();
                                    completed.countDown();
                                }
                            };
                        }
                    })
                    .build().start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopServer() {
        server.shutdownNow();
    }

    @Autowired
    private SimulationManager manager;

    @Test
    void streamsReadingsWithTheGivenSessionIdUntilStopped() throws InterruptedException {
        manager.start("session-42", "CP-TUN-002");
        Thread.sleep(2500);

        manager.stop("session-42");

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSizeGreaterThanOrEqualTo(2);
        assertThat(received).allSatisfy(value -> {
            assertThat(value.getSessionId()).isEqualTo("session-42");
            assertThat(value.getChargerId()).isEqualTo("CP-TUN-002");
        });
        // energie cumulee croissante
        assertThat(received.get(received.size() - 1).getEnergyKwh())
                .isGreaterThan(received.get(0).getEnergyKwh());
        assertThat(manager.running()).isEmpty();
    }
}
