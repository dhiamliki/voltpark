package tn.voltpark.charger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import tn.voltpark.charger.client.SimulatorClient;
import tn.voltpark.charger.model.AuthorizedBadge;
import tn.voltpark.charger.model.ChargerStation;
import tn.voltpark.charger.repository.AuthorizedBadgeRepository;
import tn.voltpark.charger.repository.ChargerStationRepository;
import tn.voltpark.charger.ws.ChargerStatus;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/**
 * Start/stop SOAP previennent le simulateur. Ici le simulateur est injoignable
 * (port 1) : la reponse SOAP doit quand meme etre ACCEPTED, sans fault.
 */
@SpringBootTest(properties = {
        "voltpark.simulator.url=http://localhost:1",
        "voltpark.simulator.connect-timeout-ms=200"
})
@ActiveProfiles("test")
class SimulatorNotificationTest {

    private static final Map<String, String> NS = Map.of("ns2", "http://voltpark.tn/charger/ws");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ChargerStationRepository chargerRepository;

    @Autowired
    private AuthorizedBadgeRepository badgeRepository;

    @SpyBean
    private SimulatorClient simulatorClient;

    private MockWebServiceClient client;

    @BeforeEach
    void setup() {
        client = MockWebServiceClient.createClient(applicationContext);
        chargerRepository.deleteAll();
        badgeRepository.deleteAll();
        chargerRepository.save(new ChargerStation("CP-SIM-001", ChargerStatus.AVAILABLE));
        badgeRepository.save(new AuthorizedBadge("TAG-001", "driver-1", true));
    }

    @Test
    void startAndStopNotifySimulatorWithSessionIdEvenWhenItIsDown() {
        client.sendRequest(withPayload(new StringSource("""
                        <ns2:RemoteStartTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                            <ns2:chargerId>CP-SIM-001</ns2:chargerId>
                            <ns2:idTag>TAG-001</ns2:idTag>
                            <ns2:sessionId>session-abc</ns2:sessionId>
                        </ns2:RemoteStartTransactionRequest>
                        """)))
                .andExpect(noFault())
                .andExpect(xpath("//ns2:status", NS).evaluatesTo("ACCEPTED"));
        verify(simulatorClient).startSimulation("session-abc", "CP-SIM-001");

        int txId = chargerRepository.findByChargerId("CP-SIM-001").orElseThrow().getCurrentTransactionId();
        client.sendRequest(withPayload(new StringSource("""
                        <ns2:RemoteStopTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                            <ns2:chargerId>CP-SIM-001</ns2:chargerId>
                            <ns2:transactionId>%d</ns2:transactionId>
                        </ns2:RemoteStopTransactionRequest>
                        """.formatted(txId))))
                .andExpect(noFault())
                .andExpect(xpath("//ns2:status", NS).evaluatesTo("ACCEPTED"));
        verify(simulatorClient).stopSimulation("session-abc");
    }

    @Test
    void startWithoutSessionIdDoesNotCallSimulator() {
        client.sendRequest(withPayload(new StringSource("""
                        <ns2:RemoteStartTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                            <ns2:chargerId>CP-SIM-001</ns2:chargerId>
                            <ns2:idTag>TAG-001</ns2:idTag>
                        </ns2:RemoteStartTransactionRequest>
                        """)))
                .andExpect(noFault());
        verify(simulatorClient, never()).startSimulation(anyString(), anyString());
    }
}
