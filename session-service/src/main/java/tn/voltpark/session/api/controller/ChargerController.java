package tn.voltpark.session.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tn.voltpark.session.api.dto.ChargerRequest;
import tn.voltpark.session.api.dto.ChargerResponse;
import tn.voltpark.session.client.soap.ChargerSoapClient;
import tn.voltpark.session.domain.model.Charger;
import tn.voltpark.session.domain.service.StationService;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chargers")
public class ChargerController {

    private final StationService stationService;
    private final ChargerSoapClient chargerSoapClient;

    public ChargerController(StationService stationService, ChargerSoapClient chargerSoapClient) {
        this.stationService = stationService;
        this.chargerSoapClient = chargerSoapClient;
    }

    @PostMapping
    public ResponseEntity<ChargerResponse> createCharger(@Valid @RequestBody ChargerRequest request) {
        ChargerResponse created = stationService.createCharger(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ChargerResponse getCharger(@PathVariable String id) {
        return stationService.getCharger(id);
    }

    @GetMapping
    public List<ChargerResponse> getAllChargers() {
        return stationService.getAllChargers();
    }

    @GetMapping("/{id}/live-status")
    public Map<String, Object> getLiveStatus(@PathVariable String id) {
        Charger charger = stationService.findChargerEntity(id);
        var soapStatus = chargerSoapClient.getChargerStatus(id);
        return Map.of(
                "chargerId", id,
                "dbStatus", charger.getStatus(),
                "liveSoapStatus", soapStatus != null ? soapStatus.value() : "UNKNOWN"
        );
    }
}
