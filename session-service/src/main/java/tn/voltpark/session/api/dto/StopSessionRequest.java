package tn.voltpark.session.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StopSessionRequest(
        BigDecimal totalEnergyKwh,
        BigDecimal peakKwh,
        BigDecimal offPeakKwh,
        Instant chargeCompletedAt
) {}
