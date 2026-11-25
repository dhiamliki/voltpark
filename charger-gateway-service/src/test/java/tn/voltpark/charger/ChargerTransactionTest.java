package tn.voltpark.charger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.ws.wsdl.wsdl11.Wsdl11Definition;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;
import tn.voltpark.charger.client.SimulatorClient;
import tn.voltpark.charger.model.AuthorizedBadge;
import tn.voltpark.charger.model.ChargerStation;
import tn.voltpark.charger.repository.AuthorizedBadgeRepository;
import tn.voltpark.charger.repository.ChargerStationRepository;
import tn.voltpark.charger.repository.ChargerTransactionRepository;
import tn.voltpark.charger.service.ChargerGatewayService;
import tn.voltpark.charger.ws.ChargerStatus;
import tn.voltpark.charger.ws.RemoteStartTransactionRequest;

import javax.xml.transform.TransformerFactory;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/** Numeros de transaction en base, controle du transactionId a l'arret, operations du WSDL. */
@SpringBootTest
@ActiveProfiles("test")
class ChargerTransactionTest {

    private static final Map<String, String> NS = Map.of("ns2", "http://voltpark.tn/charger/ws");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ChargerStationRepository chargerRepository;

    @Autowired
    private AuthorizedBadgeRepository badgeRepository;

    @Autowired
    private ChargerTransactionRepository transactionRepository;

    @Autowired
    private ChargerGatewayService service;

    @Autowired
    private SimulatorClient simulatorClient;

    @Autowired
    @Qualifier("charger")
    private Wsdl11Definition wsdl;

    private MockWebServiceClient client;

    @BeforeEach
    void setup() {
        client = MockWebServiceClient.createClient(applicationContext);
        chargerRepository.deleteAll();
        badgeRepository.deleteAll();
        chargerRepository.save(new ChargerStation("CP-TX-001", ChargerStatus.AVAILABLE));
        chargerRepository.save(new ChargerStation("CP-TX-002", ChargerStatus.AVAILABLE));
        badgeRepository.save(new AuthorizedBadge("TAG-001", "driver-1", true));
    }

    private static RemoteStartTransactionRequest start(String chargerId) {
        RemoteStartTransactionRequest request = new RemoteStartTransactionRequest();
        request.setChargerId(chargerId);
        request.setConnectorId(1);
        request.setIdTag("TAG-001");
        return request;
    }

    private void stop(String chargerId, int transactionId) {
        client.sendRequest(withPayload(new StringSource("""
                        <ns2:RemoteStopTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                            <ns2:chargerId>%s</ns2:chargerId>
                            <ns2:transactionId>%d</ns2:transactionId>
                        </ns2:RemoteStopTransactionRequest>
                        """.formatted(chargerId, transactionId))))
                .andExpect(noFault());
    }

    @Test
    void transactionIdComesFromTheDatabaseAndSurvivesARestart() {
        int first = service.remoteStart(start("CP-TX-001")).getTransactionId();
        assertThat(transactionRepository.findById(first)).isPresent();
        assertThat(transactionRepository.findById(first).orElseThrow().getChargerId()).isEqualTo("CP-TX-001");
        stop("CP-TX-001", first);
        assertThat(transactionRepository.findById(first).orElseThrow().getStoppedAt()).isNotNull();

        // "redemarrage" : une nouvelle instance du service, sans compteur en memoire
        ChargerGatewayService restarted = new ChargerGatewayService(
                chargerRepository, badgeRepository, transactionRepository, simulatorClient);
        int second = restarted.remoteStart(start("CP-TX-002")).getTransactionId();

        // le numero continue la sequence de la base au lieu de repartir a 1001
        assertThat(second).isGreaterThan(first);
        assertThat(chargerRepository.findByChargerId("CP-TX-002").orElseThrow().getCurrentTransactionId()).isEqualTo(second);
    }

    @Test
    void stopWithAnotherTransactionIdReturnsAFault() {
        int txId = service.remoteStart(start("CP-TX-001")).getTransactionId();

        client.sendRequest(withPayload(new StringSource("""
                        <ns2:RemoteStopTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                            <ns2:chargerId>CP-TX-001</ns2:chargerId>
                            <ns2:transactionId>%d</ns2:transactionId>
                        </ns2:RemoteStopTransactionRequest>
                        """.formatted(txId + 100))))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns2:InvalidTransactionDetail/ns2:errorCode", NS).evaluatesTo("TRANSACTION_MISMATCH"))
                .andExpect(xpath("//ns2:InvalidTransactionDetail/ns2:currentTransactionId", NS).evaluatesTo(txId));

        // la transaction en cours n'a pas ete arretee
        ChargerStation charger = chargerRepository.findByChargerId("CP-TX-001").orElseThrow();
        assertThat(charger.getStatus()).isEqualTo(ChargerStatus.CHARGING);
        assertThat(charger.getCurrentTransactionId()).isEqualTo(txId);
    }

    @Test
    void stopOnAnIdleChargerReturnsAFault() {
        client.sendRequest(withPayload(new StringSource("""
                        <ns2:RemoteStopTransactionRequest xmlns:ns2="http://voltpark.tn/charger/ws">
                            <ns2:chargerId>CP-TX-002</ns2:chargerId>
                            <ns2:transactionId>1234</ns2:transactionId>
                        </ns2:RemoteStopTransactionRequest>
                        """)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns2:InvalidTransactionDetail/ns2:errorCode", NS).evaluatesTo("TRANSACTION_MISMATCH"));
    }

    @Test
    void wsdlContainsOnlyTheThreeRealOperations() throws Exception {
        StringResult xml = new StringResult();
        TransformerFactory.newInstance().newTransformer().transform(wsdl.getSource(), xml);

        Set<String> operations = new TreeSet<>();
        Matcher matcher = Pattern.compile("<wsdl:operation name=\"([A-Za-z]+)\"").matcher(xml.toString());
        while (matcher.find()) {
            operations.add(matcher.group(1));
        }
        assertThat(operations).containsExactly("GetChargerStatus", "RemoteStartTransaction", "RemoteStopTransaction");
        // les details de faute restent decrits dans le schema du WSDL
        assertThat(xml.toString()).contains("BadgeRefusedDetail", "ChargerNotFoundDetail",
                "ChargerOccupiedDetail", "InvalidTransactionDetail");
    }
}
