package tn.voltpark.session.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tn.voltpark.session.client.soap.ChargerSoapClient;
import tn.voltpark.session.support.FakeEnergyServer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Charge immediate : POST /sessions sans reservationId. La borne doit etre libre, en service et
 * non reservee par un autre conducteur dans les 30 prochaines minutes ; sinon 409 sans appel SOAP.
 */
@SpringBootTest(properties = FakeEnergyServer.ADDRESS_PROPERTY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalkInStartIntegrationTest {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private ChargerSoapClient chargerSoapClient;

    @BeforeEach
    void setUp() {
        when(chargerSoapClient.remoteStartTransaction(anyString(), anyInt(), anyString(), any(), anyString()))
                .thenReturn(new ChargerSoapClient.RemoteStartResult(true, 2001, "ok", false));
        when(chargerSoapClient.remoteStopTransaction(anyString(), anyInt())).thenReturn(true);
    }

    private ResultActions walkIn(int driverId, String chargerId) throws Exception {
        return mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":" + driverId + ",\"chargerId\":\"" + chargerId + "\"}"));
    }

    private long reserve(int driverId, String chargerId, Instant start) throws Exception {
        String body = mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":" + driverId + ",\"chargerId\":\"" + chargerId + "\",\"startTime\":\"" + start
                                + "\",\"endTime\":\"" + start.plus(1, ChronoUnit.HOURS) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void stop(String body) throws Exception {
        String id = json.readTree(body).get("id").asText();
        mvc.perform(patch("/api/v1/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"STOPPED\"}")).andExpect(status().isOk());
    }

    @Test
    void walkInStartsDirectlyOnAnAvailableCharger() throws Exception {
        String body = walkIn(3, "CP-175670-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reservationId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        verify(chargerSoapClient).remoteStartTransaction(eq("CP-175670-1"), anyInt(), eq("TAG-003"), any(), anyString());
        stop(body);
    }

    @Test
    void walkInOnAnOccupiedChargerIsRefused() throws Exception {
        // CP-460375-2 est occupee dans les donnees de demonstration
        walkIn(2, "CP-460375-2")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("CHARGER_OCCUPIED"))
                .andExpect(jsonPath("$.title").value("Borne indisponible"));
        verify(chargerSoapClient, never()).remoteStartTransaction(eq("CP-460375-2"), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void walkInOnAnOutOfServiceChargerIsRefused() throws Exception {
        // CP-175677-3 est hors service dans les donnees de demonstration
        walkIn(2, "CP-175677-3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CHARGER_OUT_OF_SERVICE"));
    }

    @Test
    void walkInOnAChargerReservedByAnotherDriverIsRefused() throws Exception {
        long reservationId = reserve(1, "CP-175904-1", Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS));

        walkIn(2, "CP-175904-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CHARGER_RESERVED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reservee par un autre conducteur")));
        verify(chargerSoapClient, never()).remoteStartTransaction(eq("CP-175904-1"), anyInt(), anyString(), any(), anyString());

        // la reservation de ce meme conducteur ne le bloque pas
        String body = walkIn(1, "CP-175904-1").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        stop(body);
        mvc.perform(delete("/api/v1/reservations/{id}", reservationId)).andExpect(status().isNoContent());
    }

    @Test
    void reservationLaterThanThirtyMinutesDoesNotBlockAWalkIn() throws Exception {
        long reservationId = reserve(1, "CP-175904-2", Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS));

        String body = walkIn(2, "CP-175904-2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        stop(body);
        mvc.perform(delete("/api/v1/reservations/{id}", reservationId)).andExpect(status().isNoContent());
    }
}
