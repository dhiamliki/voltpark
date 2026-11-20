package tn.voltpark.session.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tn.voltpark.session.api.dto.ChargerResponse;
import tn.voltpark.session.api.dto.StationRequest;
import tn.voltpark.session.api.dto.StationResponse;
import tn.voltpark.session.domain.service.StationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StationResponse createStation(@Valid @RequestBody StationRequest request) {
        return stationService.createStation(request);
    }

    @GetMapping("/{id}")
    public StationResponse getStation(@PathVariable Long id) {
        return stationService.getStation(id);
    }

    @GetMapping
    public List<StationResponse> getAllStations() {
        return stationService.getAllStations();
    }

    @GetMapping("/{id}/chargers")
    public List<ChargerResponse> getChargersForStation(@PathVariable Long id) {
        return stationService.getChargersByStation(id);
    }
}
