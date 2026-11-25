package tn.voltpark.session.client.grpc;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import tn.voltpark.energy.grpc.v1.SessionEnergy;
import tn.voltpark.session.api.error.EnergyDataNotFoundException;
import tn.voltpark.session.api.error.ResourceNotFoundException;
import tn.voltpark.session.client.soap.ChargerSoapClient;
import tn.voltpark.session.support.FakeEnergyServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** EnergyGrpcClient contre un faux energy-service gRPC (in-process). */
@SpringBootTest(properties = {
        FakeEnergyServer.ADDRESS_PROPERTY,
        "resilience4j.retry.instances.energyService.waitDuration=10ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnergyGrpcClientTest {

    private final FakeEnergyServer energy = FakeEnergyServer.INSTANCE;

    @Autowired
    private EnergyGrpcClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @MockBean
    private ChargerSoapClient chargerSoapClient;

    @BeforeEach
    void reset() {
        energy.reset();
        circuitBreakers.circuitBreaker("energyService").reset();
    }

    @Test
    void returnsSessionEnergy() {
        energy.respondWith(12.5, 5.0, 7.5);

        SessionEnergy result = client.getSessionEnergy("s-1");

        assertThat(result.getSessionId()).isEqualTo("s-1");
        assertThat(result.getTotalEnergyKwh()).isEqualTo(12.5);
        assertThat(result.getPeakKwh()).isEqualTo(5.0);
        assertThat(result.getOffPeakKwh()).isEqualTo(7.5);
        assertThat(energy.energyCalls()).isEqualTo(1);
    }

    @Test
    void mapsNotFoundToDomainExceptionWithoutRetry() {
        energy.failWith(Status.NOT_FOUND.withDescription("Unknown session"));

        assertThatThrownBy(() -> client.getSessionEnergy("unknown"))
                .isInstanceOf(EnergyDataNotFoundException.class)
                // traitee en 404 par GlobalExceptionHandler
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(energy.energyCalls()).isEqualTo(1);
    }

    @Test
    void doesNotRetryInvalidArgument() {
        energy.failWith(Status.INVALID_ARGUMENT);

        // ni reessaye, ni transforme en "indisponible" : erreur du client
        assertThatThrownBy(() -> client.getSessionEnergy(""))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThat(energy.energyCalls()).isEqualTo(1);
    }

    @Test
    void retriesUnavailableThenFallsBack() {
        energy.failWith(Status.UNAVAILABLE);

        assertThat(client.getSessionEnergy("s-2")).isNull();
        // maxAttempts = 3
        assertThat(energy.energyCalls()).isEqualTo(3);
    }

    @Test
    void openCircuitFallsBackWithoutCallingEnergyService() {
        circuitBreakers.circuitBreaker("energyService").transitionToOpenState();

        assertThat(client.getSessionEnergy("s-3")).isNull();
        assertThat(energy.energyCalls()).isZero();
    }
}
