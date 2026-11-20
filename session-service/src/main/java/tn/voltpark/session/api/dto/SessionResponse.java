package tn.voltpark.session.api.dto;

import tn.voltpark.session.domain.model.ChargingSession;
import java.math.BigDecimal;
import java.time.Instant;

public record SessionResponse(
        String id,
        Long driverId,
        String chargerId,
        Long reservationId,
        ChargingSession.SessionStatus status,
        Integer transactionId,
        Instant startedAt,
        Instant stoppedAt,
        Instant chargeCompletedAt,
        BigDecimal totalEnergyKwh,
        BigDecimal peakKwh,
        BigDecimal offPeakKwh,
        Instant createdAt
) {}
