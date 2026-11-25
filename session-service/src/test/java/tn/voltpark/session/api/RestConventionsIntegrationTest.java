package tn.voltpark.session.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import tn.voltpark.session.client.soap.ChargerSoapClient;
import tn.voltpark.session.support.FakeEnergyServer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conventions REST de session-service : statuts HTTP, Location, ProblemDetail, validation. */
@SpringBootTest(properties = {
        FakeEnergyServer.ADDRESS_PROPERTY,
        "resilience4j.retry.instances.energyService.waitDuration=10ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestConventionsIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

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
        FakeEnergyServer.INSTANCE.reset();
        FakeEnergyServer.INSTANCE.respondWith(5.0, 2.0, 3.0);
        circuitBreakers.circuitBreaker("energyService").reset();
        when(chargerSoapClient.remoteStartTransaction(anyString(), anyInt(), anyString(), any(), anyString()))
                .thenReturn(new ChargerSoapClient.RemoteStartResult(true, 2001, "ok", false));
        when(chargerSoapClient.remoteStopTransaction(anyString(), anyInt())).thenReturn(true);
    }

    private static String uniqueDriver(String email, String badge) {
        return "{\"name\":\"Test\",\"email\":\"" + email + "\",\"badgeId\":\"" + badge + "\"}";
    }

    // ------------------------------------------------------------------ 201 + Location

    @Test
    void createReturns201WithLocationOfTheNewResource() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uniqueDriver("loc@voltpark.tn", "TAG-LOC")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/drivers/\\d+")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(location).endsWith("/api/v1/drivers/" + id);
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("loc@voltpark.tn"));
    }

    @Test
    void locationUsesTheGatewayAddressFromForwardedHeaders() throws Exception {
        mvc.perform(post("/api/v1/drivers")
                        .header("X-Forwarded-Proto", "http")
                        .header("X-Forwarded-Host", "localhost:18080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uniqueDriver("fwd@voltpark.tn", "TAG-FWD")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost:18080/api/v1/drivers/\\d+")));
    }

    @Test
    void createStationAndChargerReturnLocation() throws Exception {
        MvcResult station = mvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sousse\",\"latitude\":35.8,\"longitude\":10.6,\"pricePerKwh\":0.4,"
                                + "\"peakPricePerKwh\":0.5,\"offPeakPricePerKwh\":0.3,\"idleFeePerMinute\":0.1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/stations/\\d+")))
                .andReturn();
        long stationId = json.readTree(station.getResponse().getContentAsString()).get("id").asLong();

        String charger = "{\"id\":\"CP-SOU-001\",\"stationId\":" + stationId + ",\"maxPowerKw\":22}";
        mvc.perform(post("/api/v1/chargers").contentType(MediaType.APPLICATION_JSON).content(charger))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/chargers/CP-SOU-001"));
        // meme identifiant : 409 au lieu d'ecraser la borne existante
        mvc.perform(post("/api/v1/chargers").contentType(MediaType.APPLICATION_JSON).content(charger))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON));
    }

    // ------------------------------------------------------------------ 400

    @Test
    void validationErrorsReturn400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"phoneNumber\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams.name").exists())
                .andExpect(jsonPath("$.invalidParams.email").exists())
                .andExpect(jsonPath("$.invalidParams.badgeId").exists())
                .andExpect(jsonPath("$.invalidParams.phoneNumber").exists());
    }

    @Test
    void rangesAreValidated() throws Exception {
        mvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"latitude\":95,\"longitude\":-200,\"pricePerKwh\":-1,"
                                + "\"peakPricePerKwh\":0.5,\"offPeakPricePerKwh\":0.3,\"idleFeePerMinute\":0.1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams.latitude").exists())
                .andExpect(jsonPath("$.invalidParams.longitude").exists())
                .andExpect(jsonPath("$.invalidParams.pricePerKwh").exists());

        mvc.perform(post("/api/v1/chargers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"CP-BAD\",\"stationId\":1,\"maxPowerKw\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams.maxPowerKw").exists());

        mvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"CP-TUN-001\",\"connectorId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams.connectorId").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void typeMismatchReturns400() throws Exception {
        mvc.perform(get("/api/v1/drivers/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON));
        mvc.perform(get("/api/v1/sessions").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 404 / 405

    @Test
    void unknownIdReturns404() throws Exception {
        mvc.perform(get("/api/v1/drivers/99999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Ressource introuvable"));
    }

    @Test
    void unknownUrlReturns404() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mvc.perform(delete("/api/v1/drivers/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(header().exists("Allow"));
    }

    @Test
    void oldVerbUrisAreGone() throws Exception {
        mvc.perform(post("/api/v1/sessions/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":2,\"chargerId\":\"CP-TUN-001\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/sessions/some-id/stop")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 409

    @Test
    void duplicateEmailOrBadgeReturns409() throws Exception {
        mvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uniqueDriver("dhia@voltpark.tn", "TAG-NEW-1")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
        mvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uniqueDriver("new-badge@voltpark.tn", "TAG-001")))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 204

    @Test
    void deleteReservationReturns204WithoutBody() throws Exception {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        MvcResult created = mvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":1,\"chargerId\":\"CP-TUN-003\",\"startTime\":\"" + start
                                + "\",\"endTime\":\"" + start.plus(1, ChronoUnit.HOURS) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/reservations/\\d+")))
                .andReturn();
        String location = created.getResponse().getHeader("Location");

        mvc.perform(delete(location))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get(location)).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------------ PATCH stop

    @Test
    void patchStatusStoppedStopsTheSessionOnce() throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":1,\"chargerId\":\"CP-TUN-002\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/sessions/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String location = started.getResponse().getHeader("Location");

        // autre valeur que STOPPED : 400
        mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
        // valeur inconnue : 400
        mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isBadRequest());
        // statut manquant : 400 avec le champ en erreur
        mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams.status").exists());

        mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"STOPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalEnergyKwh").value(5.0));

        // deja arretee : 409
        mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"STOPPED\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON));
    }

    @Test
    void patchUnknownSessionReturns404() throws Exception {
        mvc.perform(patch("/api/v1/sessions/unknown").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"STOPPED\"}"))
                .andExpect(status().isNotFound());
    }
}
