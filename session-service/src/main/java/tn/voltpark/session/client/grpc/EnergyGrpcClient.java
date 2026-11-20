package tn.voltpark.session.client.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.voltpark.energy.grpc.v1.EnergyServiceGrpc;
import tn.voltpark.energy.grpc.v1.GetSessionEnergyRequest;
import tn.voltpark.energy.grpc.v1.SessionEnergy;

import java.util.concurrent.TimeUnit;

@Service
public class EnergyGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(EnergyGrpcClient.class);

    @GrpcClient("energy-service")
    private EnergyServiceGrpc.EnergyServiceBlockingStub energyStub;

    public SessionEnergy getSessionEnergy(String sessionId) {
        if (energyStub == null) {
            log.warn("EnergyService gRPC stub is null; skipping gRPC energy fetch for session {}", sessionId);
            return null;
        }

        try {
            log.info("Querying energy-service gRPC for session {}", sessionId);
            GetSessionEnergyRequest request = GetSessionEnergyRequest.newBuilder()
                    .setSessionId(sessionId)
                    .build();

            return energyStub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getSessionEnergy(request);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC call to energy-service failed for session {}: {}", sessionId, e.getStatus());
            return null;
        } catch (Exception e) {
            log.warn("Unexpected error calling energy-service gRPC for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }
}
