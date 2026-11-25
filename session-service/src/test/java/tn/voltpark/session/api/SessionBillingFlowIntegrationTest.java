package tn.voltpark.session.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tn.voltpark.energy.grpc.v1.SessionProgress;
import tn.voltpark.session.client.soap.ChargerSoapClient;
import tn.voltpark.session.support.FakeEnergyServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours start -> stop -> facture a travers l'API REST, avec la borne SOAP simulee (mock)
 * et un faux energy-service gRPC. Station Tunis Centre : 0.550 TND/kWh HP, 0.350 TND/kWh HC.
 */
@SpringBootTest(properties = {
        FakeEnergyServer.ADDRESS_PROPERTY,
        "resilience4j.retry.instances.energyService.waitDuration=10ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionBillingFlowIntegrationTest {

    private final FakeEnergyServer energy = FakeEnergyServer.INSTANCE;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @MockBean
    private ChargerSoapClient chargerSoapClient;

    @BeforeEach
    void setUp() {
        energy.reset();
        circuitBreakers.circuitBreaker("energyService").reset();
        when(chargerSoapClient.remoteStartTransaction(anyString(), anyInt(), anyString(), any(), anyString()))
                .thenReturn(new ChargerSoapClient.RemoteStartResult(true, 1001, "ok", false));
        when(chargerSoapClient.remoteStopTransaction(anyString(), anyInt())).thenReturn(true);
    }

    private String startSession(String chargerId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"" + chargerId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String sessionId = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        // le meme identifiant part vers la borne, qui le reutilise pour ses mesures
        verify(chargerSoapClient).remoteStartTransaction(eq(chargerId), anyInt(), eq("TAG-002"), any(), eq(sessionId));
        return sessionId;
    }

    /** PATCH /sessions/{id} {"status":"STOPPED"} */
    private static MockHttpServletRequestBuilder stop(String sessionId) {
        return patch("/api/v1/sessions/{id}", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"STOPPED\"}");
    }

    private JsonNode invoiceOf(String sessionId) throws Exception {
        String body = mvc.perform(get("/api/v1/sessions/{id}/invoice", sessionId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void stopBillsEnergyMeasuredByEnergyService() throws Exception {
        String sessionId = startSession("CP-TUN-001");
        energy.respondWith(10.0, 4.0, 6.0);

        mvc.perform(stop(sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalEnergyKwh").value(10.0));

        JsonNode invoice = invoiceOf(sessionId);
        assertThat(invoice.get("status").asText()).isEqualTo("ISSUED");
        assertThat(invoice.get("peakKwh").decimalValue()).isEqualByComparingTo("4.000");
        assertThat(invoice.get("offPeakKwh").decimalValue()).isEqualByComparingTo("6.000");
        assertThat(invoice.get("peakEnergyCost").decimalValue()).isEqualByComparingTo("2.200");
        assertThat(invoice.get("offPeakEnergyCost").decimalValue()).isEqualByComparingTo("2.100");
        assertThat(invoice.get("totalAmount").decimalValue()).isEqualByComparingTo("4.300");
    }

    @Test
    void energyServiceDownGivesPendingInvoiceThatCanBeRecalculated() throws Exception {
        String sessionId = startSession("CP-TUN-002");
        energy.failWith(Status.UNAVAILABLE);

        // l'arret reussit quand meme
        mvc.perform(stop(sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        JsonNode pending = invoiceOf(sessionId);
        assertThat(pending.get("status").asText()).isEqualTo("PENDING_ENERGY");
        assertThat(pending.get("totalAmount").decimalValue()).isEqualByComparingTo("0");
        long invoiceId = pending.get("id").asLong();

        // toujours indisponible : 503
        mvc.perform(post("/api/v1/invoices/{id}/recalculation", invoiceId))
                .andExpect(status().isServiceUnavailable());

        // energy-service revenu
        circuitBreakers.circuitBreaker("energyService").reset();
        energy.respondWith(10.0, 4.0, 6.0);
        mvc.perform(post("/api/v1/invoices/{id}/recalculation", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.totalAmount").value(4.3));

        // une facture deja emise ne se recalcule pas
        mvc.perform(post("/api/v1/invoices/{id}/recalculation", invoiceId))
                .andExpect(status().isConflict());
    }

    @Test
    void sessionWithoutReadingsIsBilledZero() throws Exception {
        String sessionId = startSession("CP-TUN-003");
        energy.failWith(Status.NOT_FOUND);

        mvc.perform(stop(sessionId)).andExpect(status().isOk());

        JsonNode invoice = invoiceOf(sessionId);
        assertThat(invoice.get("status").asText()).isEqualTo("ISSUED");
        assertThat(invoice.get("totalEnergyKwh").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void liveViewRelaysWatchSessionAsServerSentEvents() throws Exception {
        String sessionId = startSession("CP-TUN-001");
        energy.streamProgress(List.of(progress(1.25, 40), progress(2.5, 42)));

        MvcResult result = mvc.perform(get("/api/v1/sessions/{id}/live", sessionId)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);
        String body = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body).contains("event:progress");
        assertThat(body).contains("\"energyKwh\":1.25").contains("\"energyKwh\":2.5").contains("\"socPercent\":42");
        assertThat(body).contains("event:end");

        mvc.perform(stop(sessionId)).andExpect(status().isOk());
    }

    @Test
    void liveViewOfUnknownSessionIs404() throws Exception {
        mvc.perform(get("/api/v1/sessions/{id}/live", "does-not-exist").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    private static SessionProgress progress(double energyKwh, int soc) {
        return SessionProgress.newBuilder()
                .setTimestamp(Timestamp.newBuilder().setSeconds(1_790_000_000L))
                .setPowerKw(11.0)
                .setEnergyKwh(energyKwh)
                .setSocPercent(soc)
                .setEstimatedMinutesRemaining(30)
                .build();
    }
}
