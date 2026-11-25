package tn.voltpark.session.support;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.GetSessionEnergyRequest;
import tn.voltpark.energy.grpc.v1.SessionEnergy;
import tn.voltpark.energy.grpc.v1.SessionProgress;
import tn.voltpark.energy.grpc.v1.WatchSessionRequest;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Faux energy-service gRPC, en memoire (in-process). Les tests choisissent la reponse :
 * un bilan d'energie, ou une erreur gRPC (NOT_FOUND, UNAVAILABLE...).
 * Les tests utilisent grpc.client.energy-service.address=in-process:fake-energy.
 */
public final class FakeEnergyServer extends EnergyServiceGrpc.EnergyServiceImplBase {

    public static final String NAME = "fake-energy";
    public static final String ADDRESS_PROPERTY = "grpc.client.energy-service.address=in-process:" + NAME;
    public static final FakeEnergyServer INSTANCE = new FakeEnergyServer();

    static {
        try {
            Server server = InProcessServerBuilder.forName(NAME).addService(INSTANCE).build().start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private final AtomicInteger energyCalls = new AtomicInteger();
    private volatile Status failure;
    private volatile double totalKwh;
    private volatile double peakKwh;
    private volatile double offPeakKwh;
    private volatile List<SessionProgress> progress = List.of();

    private FakeEnergyServer() {
    }

    public void reset() {
        energyCalls.set(0);
        failure = null;
        totalKwh = 0;
        peakKwh = 0;
        offPeakKwh = 0;
        progress = List.of();
    }

    public void respondWith(double total, double peak, double offPeak) {
        failure = null;
        totalKwh = total;
        peakKwh = peak;
        offPeakKwh = offPeak;
    }

    public void failWith(Status status) {
        failure = status;
    }

    public void streamProgress(List<SessionProgress> values) {
        failure = null;
        progress = values;
    }

    public int energyCalls() {
        return energyCalls.get();
    }

    @Override
    public void getSessionEnergy(GetSessionEnergyRequest request, StreamObserver<SessionEnergy> response) {
        energyCalls.incrementAndGet();
        if (failure != null) {
            response.onError(failure.asRuntimeException());
            return;
        }
        response.onNext(SessionEnergy.newBuilder()
                .setSessionId(request.getSessionId())
                .setTotalEnergyKwh(totalKwh)
                .setPeakKwh(peakKwh)
                .setOffPeakKwh(offPeakKwh)
                .build());
        response.onCompleted();
    }

    @Override
    public void watchSession(WatchSessionRequest request, StreamObserver<SessionProgress> response) {
        if (failure != null) {
            response.onError(failure.asRuntimeException());
            return;
        }
        for (SessionProgress value : progress) {
            response.onNext(value.toBuilder().setSessionId(request.getSessionId()).build());
        }
        response.onCompleted();
    }
}
