package tn.voltpark.session.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tn.voltpark.session.api.dto.InvoiceResponse;
import tn.voltpark.session.api.dto.SessionResponse;
import tn.voltpark.session.api.dto.StartSessionRequest;
import tn.voltpark.session.api.dto.StopSessionRequest;
import tn.voltpark.session.domain.model.ChargingSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.voltpark.session.domain.service.SessionLiveService;
import tn.voltpark.session.domain.service.SessionManagementService;

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

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse startSession(@Valid @RequestBody StartSessionRequest request) {
        return sessionService.startSession(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse startSessionDirect(@Valid @RequestBody StartSessionRequest request) {
        return sessionService.startSession(request);
    }

    @PostMapping("/{id}/stop")
    public SessionResponse stopSession(
            @PathVariable String id,
            @RequestBody(required = false) StopSessionRequest request) {
        return sessionService.stopSession(id, request);
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
