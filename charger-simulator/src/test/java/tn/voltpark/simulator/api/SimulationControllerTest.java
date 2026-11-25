package tn.voltpark.simulator.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tn.voltpark.simulator.simulator.MeterValueStreamer;
import tn.voltpark.simulator.simulator.SimulatedSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API start/stop du simulateur. Le flux gRPC est remplace par un faux qui attend l'arret. */
@SpringBootTest(properties = "simulator.enabled=false")
@AutoConfigureMockMvc
class SimulationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MeterValueStreamer streamer;

    private final List<String> finished = new CopyOnWriteArrayList<>();

    private void fakeStreamWaitingForStop() {
        doAnswer(invocation -> {
            SimulatedSession session = invocation.getArgument(0);
            while (!session.isStopRequested()) {
                Thread.sleep(20);
            }
            finished.add(session.sessionId());
            return null;
        }).when(streamer).streamSession(any(), anyInt());
    }

    private static String body(String sessionId, String chargerId) {
        return "{\"sessionId\":\"" + sessionId + "\",\"chargerId\":\"" + chargerId + "\"}";
    }

    @Test
    void startsAndStopsSeveralSessionsInParallel() throws Exception {
        fakeStreamWaitingForStop();

        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content(body("s-1", "CP-TUN-001")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/simulations/s-1"))
                .andExpect(jsonPath("$.sessionId").value("s-1"))
                .andExpect(jsonPath("$.chargerId").value("CP-TUN-001"));
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content(body("s-2", "CP-TUN-002")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/simulations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(delete("/api/simulations/s-1")).andExpect(status().isNoContent());
        // la reponse arrive apres la fin du flux de s-1 ; s-2 continue
        assertThat(finished).containsExactly("s-1");
        mvc.perform(get("/api/simulations"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value("s-2"));

        mvc.perform(delete("/api/simulations/s-2")).andExpect(status().isNoContent());
        assertThat(finished).containsExactly("s-1", "s-2");
    }

    @Test
    void rejectsSecondStartForSameSession() throws Exception {
        fakeStreamWaitingForStop();
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content(body("dup", "CP-TUN-001")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content(body("dup", "CP-TUN-001")))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/simulations/dup")).andExpect(status().isNoContent());
    }

    @Test
    void returns404ForUnknownSession() throws Exception {
        mvc.perform(delete("/api/simulations/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void validatesRequestBody() throws Exception {
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
