package tn.voltpark.charger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import tn.voltpark.charger.model.AuthorizedBadge;
import tn.voltpark.charger.model.ChargerStation;
import tn.voltpark.charger.repository.AuthorizedBadgeRepository;
import tn.voltpark.charger.repository.ChargerStationRepository;
import tn.voltpark.charger.ws.ChargerStatus;

import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
public class ChargerSoapIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ChargerStationRepository chargerRepository;

    @Autowired
    private AuthorizedBadgeRepository badgeRepository;

    private MockWebServiceClient client;

    @BeforeEach
    public void setup() {
        client = MockWebServiceClient.createClient(applicationContext);

        chargerRepository.deleteAll();
        badgeRepository.deleteAll();

        chargerRepository.save(new ChargerStation("CP-TUN-001", ChargerStatus.AVAILABLE));
        ChargerStation occupied = new ChargerStation("CP-TUN-OCCUPIED", ChargerStatus.OCCUPIED);
        occupied.setCurrentTransactionId(9999);
        occupied.setCurrentIdTag("TAG-OCC");
        chargerRepository.save(occupied);

        badgeRepository.save(new AuthorizedBadge("TAG-001", "driver-1", true));
        badgeRepository.save(new AuthorizedBadge("TAG-BLOCKED", "driver-2", false));
    }

    @Test
    public void testRemoteStartTransaction_Success() {
        String request = """
                <ns2:RemoteStartTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                    <ns2:chargerId>CP-TUN-001</ns2:chargerId>
                    <ns2:connectorId>1</ns2:connectorId>
                    <ns2:idTag>TAG-001</ns2:idTag>
                </ns2:RemoteStartTransactionRequest>
                """;

        client.sendRequest(withPayload(new StringSource(request)))
                .andExpect(noFault())
                .andExpect(xpath("//ns2:status", namespaces()).evaluatesTo("ACCEPTED"))
                .andExpect(xpath("//ns2:transactionId", namespaces()).exists());
    }

    @Test
    public void testRemoteStartTransaction_ChargerNotFound_SoapFault() {
        String request = """
                <ns2:RemoteStartTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                    <ns2:chargerId>CP-UNKNOWN</ns2:chargerId>
                    <ns2:connectorId>1</ns2:connectorId>
                    <ns2:idTag>TAG-001</ns2:idTag>
                </ns2:RemoteStartTransactionRequest>
                """;

        client.sendRequest(withPayload(new StringSource(request)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns2:ChargerNotFoundDetail/ns2:errorCode", namespaces()).evaluatesTo("CHARGER_NOT_FOUND"))
                .andExpect(xpath("//ns2:ChargerNotFoundDetail/ns2:chargerId", namespaces()).evaluatesTo("CP-UNKNOWN"));
    }

    @Test
    public void testRemoteStartTransaction_ChargerOccupied_SoapFault() {
        String request = """
                <ns2:RemoteStartTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                    <ns2:chargerId>CP-TUN-OCCUPIED</ns2:chargerId>
                    <ns2:connectorId>1</ns2:connectorId>
                    <ns2:idTag>TAG-001</ns2:idTag>
                </ns2:RemoteStartTransactionRequest>
                """;

        client.sendRequest(withPayload(new StringSource(request)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns2:ChargerOccupiedDetail/ns2:errorCode", namespaces()).evaluatesTo("CHARGER_OCCUPIED"))
                .andExpect(xpath("//ns2:ChargerOccupiedDetail/ns2:chargerId", namespaces()).evaluatesTo("CP-TUN-OCCUPIED"));
    }

    @Test
    public void testRemoteStartTransaction_BadgeRefused_SoapFault() {
        String request = """
                <ns2:RemoteStartTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                    <ns2:chargerId>CP-TUN-001</ns2:chargerId>
                    <ns2:connectorId>1</ns2:connectorId>
                    <ns2:idTag>TAG-BLOCKED</ns2:idTag>
                </ns2:RemoteStartTransactionRequest>
                """;

        client.sendRequest(withPayload(new StringSource(request)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns2:BadgeRefusedDetail/ns2:errorCode", namespaces()).evaluatesTo("BADGE_REFUSED"))
                .andExpect(xpath("//ns2:BadgeRefusedDetail/ns2:idTag", namespaces()).evaluatesTo("TAG-BLOCKED"));
    }

    @Test
    public void testGetChargerStatus() {
        String request = """
                <ns2:GetChargerStatusRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                    <ns2:chargerId>CP-TUN-001</ns2:chargerId>
                </ns2:GetChargerStatusRequest>
                """;

        client.sendRequest(withPayload(new StringSource(request)))
                .andExpect(noFault())
                .andExpect(xpath("//ns2:chargerId", namespaces()).evaluatesTo("CP-TUN-001"))
                .andExpect(xpath("//ns2:status", namespaces()).evaluatesTo("AVAILABLE"));
    }

    @Test
    public void testRemoteStopTransaction() {
        String request = """
                <ns2:RemoteStopTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                    <ns2:chargerId>CP-TUN-OCCUPIED</ns2:chargerId>
                    <ns2:transactionId>9999</ns2:transactionId>
                </ns2:RemoteStopTransactionRequest>
                """;

        client.sendRequest(withPayload(new StringSource(request)))
                .andExpect(noFault())
                .andExpect(xpath("//ns2:status", namespaces()).evaluatesTo("ACCEPTED"));
    }

    private static java.util.Map<String, String> namespaces() {
        return java.util.Map.of("ns2", "http://voltpark.tn/charger/ws");
    }
}
