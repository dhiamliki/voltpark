package tn.voltpark.session.api.dto;

import tn.voltpark.session.domain.model.Reservation;
import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long driverId,
        String chargerId,
        Instant startTime,
        Instant endTime,
        Reservation.ReservationStatus status,
        Instant createdAt
) {}
