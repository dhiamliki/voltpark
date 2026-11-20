package tn.voltpark.session.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.session.api.dto.ReservationRequest;
import tn.voltpark.session.api.dto.ReservationResponse;
import tn.voltpark.session.api.error.ReservationConflictException;
import tn.voltpark.session.api.error.ResourceNotFoundException;
import tn.voltpark.session.domain.model.Reservation;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.DriverRepository;
import tn.voltpark.session.repository.ReservationRepository;

import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final DriverRepository driverRepository;
    private final ChargerRepository chargerRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              DriverRepository driverRepository,
                              ChargerRepository chargerRepository) {
        this.reservationRepository = reservationRepository;
        this.driverRepository = driverRepository;
        this.chargerRepository = chargerRepository;
    }

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        if (!driverRepository.existsById(request.driverId())) {
            throw new ResourceNotFoundException("Conducteur", request.driverId());
        }
        if (!chargerRepository.existsById(request.chargerId())) {
            throw new ResourceNotFoundException("Borne", request.chargerId());
        }
        if (request.endTime().isBefore(request.startTime()) || request.endTime().equals(request.startTime())) {
            throw new IllegalArgumentException("L'heure de fin doit etre strictement posterieure a l'heure de debut");
        }

        List<Reservation> conflicts = reservationRepository.findOverlappingReservations(
                request.chargerId(), request.startTime(), request.endTime());
        if (!conflicts.isEmpty()) {
            throw new ReservationConflictException("La borne " + request.chargerId() + " est deja reservee sur ce creneau horaire");
        }

        Reservation reservation = new Reservation(
                request.driverId(), request.chargerId(), request.startTime(), request.endTime());
        Reservation saved = reservationRepository.save(reservation);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(Long driverId, String chargerId) {
        if (driverId != null) {
            return reservationRepository.findByDriverId(driverId).stream().map(this::toResponse).toList();
        }
        if (chargerId != null) {
            return reservationRepository.findByChargerId(chargerId).stream().map(this::toResponse).toList();
        }
        return reservationRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReservationResponse cancelReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        return toResponse(saved);
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getDriverId(),
                r.getChargerId(),
                r.getStartTime(),
                r.getEndTime(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }
}
