package tn.voltpark.session.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.session.api.dto.ChargerRequest;
import tn.voltpark.session.api.dto.ChargerResponse;
import tn.voltpark.session.api.dto.StationRequest;
import tn.voltpark.session.api.dto.StationResponse;
import tn.voltpark.session.api.error.ResourceConflictException;
import tn.voltpark.session.api.error.ResourceNotFoundException;
import tn.voltpark.session.domain.model.Charger;
import tn.voltpark.session.domain.model.Station;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.StationRepository;

import java.util.List;

@Service
public class StationService {

    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;

    public StationService(StationRepository stationRepository, ChargerRepository chargerRepository) {
        this.stationRepository = stationRepository;
        this.chargerRepository = chargerRepository;
    }

    @Transactional
    public StationResponse createStation(StationRequest request) {
        Station station = new Station(
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.pricePerKwh(),
                request.peakPricePerKwh(),
                request.offPeakPricePerKwh(),
                request.idleFeePerMinute()
        );
        Station saved = stationRepository.save(station);
        return toStationResponse(saved);
    }

    @Transactional(readOnly = true)
    public StationResponse getStation(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station", id));
        return toStationResponse(station);
    }

    @Transactional(readOnly = true)
    public List<StationResponse> getAllStations() {
        return stationRepository.findAll().stream().map(this::toStationResponse).toList();
    }

    @Transactional
    public ChargerResponse createCharger(ChargerRequest request) {
        if (chargerRepository.existsById(request.id())) {
            throw new ResourceConflictException("Une borne avec cet identifiant existe deja: " + request.id());
        }
        Station station = stationRepository.findById(request.stationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station", request.stationId()));

        Charger.ChargerType type = request.chargerType() != null ? request.chargerType() : Charger.ChargerType.SOAP;
        Charger charger = new Charger(request.id(), station, type, Charger.ChargerStatus.AVAILABLE, request.maxPowerKw());
        Charger saved = chargerRepository.save(charger);
        return toChargerResponse(saved);
    }

    @Transactional(readOnly = true)
    public ChargerResponse getCharger(String id) {
        Charger charger = chargerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borne", id));
        return toChargerResponse(charger);
    }

    @Transactional(readOnly = true)
    public List<ChargerResponse> getChargersByStation(Long stationId) {
        return chargerRepository.findByStationId(stationId).stream().map(this::toChargerResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChargerResponse> getAllChargers() {
        return chargerRepository.findAll().stream().map(this::toChargerResponse).toList();
    }

    @Transactional(readOnly = true)
    public Charger findChargerEntity(String id) {
        return chargerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borne", id));
    }

    private StationResponse toStationResponse(Station s) {
        List<ChargerResponse> chargers = s.getChargers() != null ?
                s.getChargers().stream().map(this::toChargerResponse).toList() : List.of();
        return new StationResponse(
                s.getId(), s.getName(), s.getAddress(), s.getLatitude(), s.getLongitude(),
                s.getPricePerKwh(), s.getPeakPricePerKwh(), s.getOffPeakPricePerKwh(), s.getIdleFeePerMinute(),
                chargers
        );
    }

    private ChargerResponse toChargerResponse(Charger c) {
        return new ChargerResponse(
                c.getId(),
                c.getStation() != null ? c.getStation().getId() : null,
                c.getStation() != null ? c.getStation().getName() : null,
                c.getChargerType(),
                c.getStatus(),
                c.getMaxPowerKw()
        );
    }
}
