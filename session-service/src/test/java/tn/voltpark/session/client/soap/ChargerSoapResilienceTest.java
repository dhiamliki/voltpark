package tn.voltpark.session.client.soap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.xml.transform.StringSource;
import tn.voltpark.session.api.error.ChargerFaultException;
import tn.voltpark.session.client.soap.model.RemoteStartStopStatus;
import tn.voltpark.session.client.soap.model.RemoteStartTransactionResponse;
import tn.voltpark.session.domain.model.Charger;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.ChargingSessionRepository;
import tn.voltpark.session.support.FakeEnergyServer;

import javax.xml.transform.TransformerFactory;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Client SOAP avec Resilience4j : le WebServiceTemplate est simule pour produire soit une
 * SOAP Fault metier, soit une panne reseau. Profil test : 2 essais, 100 ms d'attente.
 */
@SpringBootTest(properties = FakeEnergyServer.ADDRESS_PROPERTY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChargerSoapResilienceTest {

    private static final String NS = "http://voltpark.tn/charger/ws";

    @Autowired
    private ChargerSoapClient client;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private ChargingSessionRepository sessionRepository;

    @MockBean
    private WebServiceTemplate webServiceTemplate;

    private CircuitBreaker circuit;

    @BeforeEach
    void setUp() {
        reset(webServiceTemplate);
        circuit = circuitBreakers.circuitBreaker("chargerGateway");
        circuit.reset();
        FakeEnergyServer.INSTANCE.reset();
        FakeEnergyServer.INSTANCE.respondWith(4.0, 4.0, 0.0);
        chargerRepository.findAll().forEach(c -> {
            c.setStatus(Charger.ChargerStatus.AVAILABLE);
            chargerRepository.save(c);
        });
    }

    /** SOAP Fault "Client" avec un detail contenant errorCode, comme charger-gateway-service. */
    private static SoapFaultClientException soapFault(String detailElement, String errorCode, String message) throws Exception {
        SoapMessage soapMessage = new SaajSoapMessageFactory(jakarta.xml.soap.MessageFactory.newInstance())
                .createWebServiceMessage();
        SoapFault fault = soapMessage.getSoapBody().addClientOrSenderFault(message, Locale.ENGLISH);
        String detail = "<ns:" + detailElement + " xmlns:ns=\"" + NS + "\"><ns:errorCode>" + errorCode
                + "</ns:errorCode><ns:message>" + message + "</ns:message></ns:" + detailElement + ">";
        TransformerFactory.newInstance().newTransformer()
                .transform(new StringSource(detail), fault.addFaultDetail().getResult());
        return new SoapFaultClientException(soapMessage);
    }

    private static RemoteStartTransactionResponse accepted(int transactionId) {
        RemoteStartTransactionResponse response = new RemoteStartTransactionResponse();
        response.setStatus(RemoteStartStopStatus.ACCEPTED);
        response.setTransactionId(transactionId);
        return response;
    }

    // ------------------------------------------------------------------ client

    @Test
    void soapFaultBecomesBusinessExceptionAndIsNotRetried() throws Exception {
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(soapFault("BadgeRefusedDetail", "BADGE_REFUSED", "Badge refuse: TAG-BLOCKED"));

        assertThatThrownBy(() -> client.remoteStartTransaction("CP-TUN-001", 1, "TAG-BLOCKED", null, "s-1"))
                .isInstanceOfSatisfying(ChargerFaultException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getErrorCode()).isEqualTo("BADGE_REFUSED");
                });

        // un seul appel : pas de nouvel essai
        verify(webServiceTemplate, times(1)).marshalSendAndReceive(any());
        // ignoree par le circuit breaker : ni echec, ni ouverture
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void faultCodesMapToHttpStatuses() throws Exception {
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(soapFault("ChargerNotFoundDetail", "CHARGER_NOT_FOUND", "Borne inconnue"))
                .thenThrow(soapFault("ChargerOccupiedDetail", "CHARGER_OCCUPIED", "Borne occupee"));

        assertThatThrownBy(() -> client.remoteStartTransaction("X", 1, "TAG-001", null, "s"))
                .isInstanceOfSatisfying(ChargerFaultException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.remoteStartTransaction("X", 1, "TAG-001", null, "s"))
                .isInstanceOfSatisfying(ChargerFaultException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void connectionFailureIsRetriedThenFallsBackToPendingStart() {
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(new WebServiceIOException("I/O error: Connection refused", new IOException("Connection refused")));

        ChargerSoapClient.RemoteStartResult result = client.remoteStartTransaction("CP-TUN-001", 1, "TAG-001", null, "s-2");

        assertThat(result.fallbackTriggered()).isTrue();
        assertThat(result.message()).contains("PENDING_START");
        // maxAttempts = 2 dans le profil de test : le retry a vraiment refait l'appel
        verify(webServiceTemplate, times(2)).marshalSendAndReceive(any());
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    void openCircuitFallsBackWithoutCallingTheGateway() {
        circuit.transitionToOpenState();

        ChargerSoapClient.RemoteStartResult result = client.remoteStartTransaction("CP-TUN-001", 1, "TAG-001", null, "s-3");

        assertThat(result.fallbackTriggered()).isTrue();
        verify(webServiceTemplate, times(0)).marshalSendAndReceive(any());
    }

    // ------------------------------------------------------------------ API

    @Test
    void blockedBadgeGives403WithoutPendingSessionNorOpenCircuit() throws Exception {
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(soapFault("BadgeRefusedDetail", "BADGE_REFUSED", "Badge refuse: TAG-BLOCKED"));
        long sessionsBefore = sessionRepository.count();

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"driverId\":4,\"chargerId\":\"CP-TUN-001\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("BADGE_REFUSED"));
        }

        assertThat(sessionRepository.count()).isEqualTo(sessionsBefore);
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(chargerRepository.findById("CP-TUN-001").orElseThrow().getStatus())
                .isEqualTo(Charger.ChargerStatus.AVAILABLE);
    }

    @Test
    void pendingSessionCanBeStartedLaterWithAStartAttempt() throws Exception {
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(new WebServiceIOException("Connection refused"));

        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":1,\"chargerId\":\"CP-TUN-002\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_START"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        assertThat(chargerRepository.findById("CP-TUN-002").orElseThrow().getStatus())
                .isEqualTo(Charger.ChargerStatus.OCCUPIED);

        // passerelle toujours en panne : 502, la session reste en attente
        circuit.reset();
        mvc.perform(post("/api/v1/sessions/{id}/start-attempts", id))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/api/v1/sessions/{id}", id)).andExpect(jsonPath("$.status").value("PENDING_START"));

        // passerelle revenue : la session demarre
        circuit.reset();
        reset(webServiceTemplate);
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(accepted(3001));
        mvc.perform(post("/api/v1/sessions/{id}/start-attempts", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.transactionId").value(3001));
        assertThat(chargerRepository.findById("CP-TUN-002").orElseThrow().getStatus())
                .isEqualTo(Charger.ChargerStatus.CHARGING);

        // un nouvel essai sur une session deja demarree : 409
        mvc.perform(post("/api/v1/sessions/{id}/start-attempts", id)).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"STOPPED\"}")).andExpect(status().isOk());
    }

    @Test
    void cancellingAPendingSessionReleasesTheCharger() throws Exception {
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(new WebServiceIOException("Connection refused"));
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":1,\"chargerId\":\"CP-TUN-003\"}"))
                .andExpect(jsonPath("$.status").value("PENDING_START"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();

        // la borne est gardee pour cette session : un autre demarrage est refuse
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"CP-TUN-003\"}"))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"STOPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(chargerRepository.findById("CP-TUN-003").orElseThrow().getStatus())
                .isEqualTo(Charger.ChargerStatus.AVAILABLE);
        // pas de facture pour une session qui n'a jamais demarre
        mvc.perform(get("/api/v1/sessions/{id}/invoice", id)).andExpect(status().isNotFound());
    }

    @Test
    void startAttemptRefusedByTheChargerFailsTheSessionAndReleasesTheCharger() throws Exception {
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(new WebServiceIOException("Connection refused"));
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"CP-TUN-001\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();

        circuit.reset();
        reset(webServiceTemplate);
        when(webServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(soapFault("ChargerOccupiedDetail", "CHARGER_OCCUPIED", "Borne occupee"));
        mvc.perform(post("/api/v1/sessions/{id}/start-attempts", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CHARGER_OCCUPIED"));

        mvc.perform(get("/api/v1/sessions/{id}", id)).andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(chargerRepository.findById("CP-TUN-001").orElseThrow().getStatus())
                .isEqualTo(Charger.ChargerStatus.AVAILABLE);
    }

    @Test
    void reservationFollowsItsSession() throws Exception {
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(accepted(4001));
        Instant start = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        String reservation = mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"CP-TUN-002\",\"startTime\":\"" + start
                                + "\",\"endTime\":\"" + start.plus(30, ChronoUnit.MINUTES) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reservationId = json.readTree(reservation).get("id").asLong();

        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"CP-TUN-002\",\"reservationId\":" + reservationId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mvc.perform(get("/api/v1/reservations/{id}", reservationId)).andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(patch("/api/v1/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"STOPPED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservations/{id}", reservationId)).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void reservationOfAnotherDriverIsRefused() throws Exception {
        Instant start = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        String reservation = mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":1,\"chargerId\":\"CP-TUN-003\",\"startTime\":\"" + start
                                + "\",\"endTime\":\"" + start.plus(10, ChronoUnit.MINUTES) + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long reservationId = json.readTree(reservation).get("id").asLong();

        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"CP-TUN-003\",\"reservationId\":" + reservationId + "}"))
                .andExpect(status().isConflict());
        verify(webServiceTemplate, times(0)).marshalSendAndReceive(any());
    }
}
