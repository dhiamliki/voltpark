package tn.voltpark.session.api.dto;

import tn.voltpark.session.domain.model.Invoice;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
        Long id,
        String sessionId,
        Long driverId,
        BigDecimal totalEnergyKwh,
        BigDecimal peakKwh,
        BigDecimal offPeakKwh,
        BigDecimal peakEnergyCost,
        BigDecimal offPeakEnergyCost,
        BigDecimal energyCost,
        Long idleDurationMinutes,
        BigDecimal idleFee,
        BigDecimal totalAmount,
        String currency,
        Invoice.InvoiceStatus status,
        Instant issuedAt
) {}
