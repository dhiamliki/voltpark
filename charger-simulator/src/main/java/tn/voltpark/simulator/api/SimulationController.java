package tn.voltpark.simulator.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tn.voltpark.simulator.simulator.SimulatedSession;
import tn.voltpark.simulator.simulator.SimulationManager;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * API interne (non publiee sur la machine hote) appelee par charger-gateway-service
 * quand une borne accepte ou arrete une transaction.
 */
@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    public record StartSimulationRequest(@NotBlank String sessionId, @NotBlank String chargerId) {
    }

    public record SimulationResponse(String sessionId, String chargerId, Instant startedAt) {

        static SimulationResponse of(SimulatedSession session) {
            return new SimulationResponse(session.sessionId(), session.chargerId(), session.startedAt());
        }
    }

    private final SimulationManager manager;

    public SimulationController(SimulationManager manager) {
        this.manager = manager;
    }

    @PostMapping
    public ResponseEntity<SimulationResponse> start(@Valid @RequestBody StartSimulationRequest request) {
        SimulatedSession session = manager.start(request.sessionId(), request.chargerId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sessionId}").buildAndExpand(session.sessionId()).toUri();
        return ResponseEntity.created(location).body(SimulationResponse.of(session));
    }

    @GetMapping
    public List<SimulationResponse> running() {
        return manager.running().stream().map(SimulationResponse::of).toList();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> stop(@PathVariable String sessionId) {
        manager.stop(sessionId);
        return ResponseEntity.noContent().build();
    }
}
