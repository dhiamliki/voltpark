package tn.voltpark.session.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tn.voltpark.session.api.dto.InvoiceResponse;
import tn.voltpark.session.api.dto.SessionResponse;
import tn.voltpark.session.api.dto.StartSessionRequest;
import tn.voltpark.session.api.dto.StopSessionRequest;
import tn.voltpark.session.domain.model.ChargingSession;
import tn.voltpark.session.domain.service.SessionLiveService;
import tn.voltpark.session.domain.service.SessionManagementService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionManagementService sessionService;
    private final SessionLiveService sessionLiveService;

    public SessionController(SessionManagementService sessionService, SessionLiveService sessionLiveService) {
        this.sessionService = sessionService;
        this.sessionLiveService = sessionLiveService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // documente le 201 dans /v3/api-docs
    public ResponseEntity<SessionResponse> startSession(@Valid @RequestBody StartSessionRequest request) {
        SessionResponse session = sessionService.startSession(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(session.id()).toUri();
        return ResponseEntity.created(location).body(session);
    }

    /**
     * Mise a jour partielle de la session. Seule transition acceptee : {"status":"STOPPED"}
     * (arret de la charge, puis facturation ; la session passe a COMPLETED).
     */
    @PatchMapping("/{id}")
    public SessionResponse updateSession(
            @PathVariable String id,
            @Valid @RequestBody StopSessionRequest request) {
        return sessionService.stopSession(id, request);
    }

    /**
     * Nouvel essai de demarrage d'une session PENDING_START (la passerelle SOAP etait injoignable).
     * L'essai est traite tout de suite : la reponse est l'etat de la session (ACTIVE si la borne accepte).
     */
    @PostMapping("/{id}/start-attempts")
    public SessionResponse retryStart(@PathVariable String id) {
        return sessionService.retryStart(id);
    }

    @GetMapping("/{id}")
    public SessionResponse getSession(@PathVariable String id) {
        return sessionService.getSession(id);
    }

    @GetMapping
    public List<SessionResponse> getSessions(
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String chargerId,
            @RequestParam(required = false) ChargingSession.SessionStatus status) {
        return sessionService.getSessions(driverId, chargerId, status);
    }

    /** Suivi en direct (Server-Sent Events) relaye depuis WatchSession d'energy-service. */
    @GetMapping(value = "/{id}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveSession(@PathVariable String id) {
        return sessionLiveService.open(id);
    }

    @GetMapping("/{id}/invoice")
    public InvoiceResponse getInvoiceForSession(@PathVariable String id) {
        return sessionService.getInvoiceBySessionId(id);
    }
}
