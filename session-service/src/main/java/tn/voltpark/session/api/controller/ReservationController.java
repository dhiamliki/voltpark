package tn.voltpark.session.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tn.voltpark.session.api.dto.ReservationRequest;
import tn.voltpark.session.api.dto.ReservationResponse;
import tn.voltpark.session.domain.service.ReservationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(@Valid @RequestBody ReservationRequest request) {
        return reservationService.createReservation(request);
    }

    @GetMapping("/{id}")
    public ReservationResponse getReservation(@PathVariable Long id) {
        return reservationService.getReservation(id);
    }

    @GetMapping
    public List<ReservationResponse> getReservations(
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String chargerId) {
        return reservationService.getReservations(driverId, chargerId);
    }

    @DeleteMapping("/{id}")
    public ReservationResponse cancelReservation(@PathVariable Long id) {
        return reservationService.cancelReservation(id);
    }
}
