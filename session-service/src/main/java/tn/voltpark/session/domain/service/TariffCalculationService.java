package tn.voltpark.session.domain.service;

import org.springframework.stereotype.Service;
import tn.voltpark.session.domain.model.Station;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class TariffCalculationService {

    public static final long DEFAULT_GRACE_PERIOD_MINUTES = 15L;

    public record CalculationResult(
            BigDecimal totalEnergyKwh,
            BigDecimal peakKwh,
            BigDecimal offPeakKwh,
            BigDecimal peakEnergyCost,
            BigDecimal offPeakEnergyCost,
            BigDecimal energyCost,
            Long idleDurationMinutes,
            BigDecimal idleFee,
            BigDecimal totalAmount
    ) {}

    /**
     * Calcule la facture selon l'energie consommee (heures pleines / creuses ou tarif fixe)
     * et les frais d'occupation apres la fin de charge.
     */
    public CalculationResult calculateInvoice(
            Station station,
            BigDecimal totalEnergyKwh,
            BigDecimal peakKwh,
            BigDecimal offPeakKwh,
            Instant chargeCompletedAt,
            Instant stoppedAt,
            long gracePeriodMinutes) {

        BigDecimal safeTotalKwh = totalEnergyKwh != null ? totalEnergyKwh : BigDecimal.ZERO;
        BigDecimal safePeakKwh = peakKwh != null ? peakKwh : BigDecimal.ZERO;
        BigDecimal safeOffPeakKwh = offPeakKwh != null ? offPeakKwh : BigDecimal.ZERO;

        BigDecimal peakCost;
        BigDecimal offPeakCost;
        BigDecimal energyCost;

        boolean hasTimeOfUseSplit = (safePeakKwh.compareTo(BigDecimal.ZERO) > 0 || safeOffPeakKwh.compareTo(BigDecimal.ZERO) > 0);

        if (hasTimeOfUseSplit) {
            BigDecimal peakPrice = station.getPeakPricePerKwh() != null ? station.getPeakPricePerKwh() : station.getPricePerKwh();
            BigDecimal offPeakPrice = station.getOffPeakPricePerKwh() != null ? station.getOffPeakPricePerKwh() : station.getPricePerKwh();

            peakCost = safePeakKwh.multiply(peakPrice).setScale(3, RoundingMode.HALF_UP);
            offPeakCost = safeOffPeakKwh.multiply(offPeakPrice).setScale(3, RoundingMode.HALF_UP);
            energyCost = peakCost.add(offPeakCost).setScale(3, RoundingMode.HALF_UP);
        } else {
            BigDecimal basePrice = station.getPricePerKwh() != null ? station.getPricePerKwh() : BigDecimal.ZERO;
            peakCost = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            offPeakCost = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            energyCost = safeTotalKwh.multiply(basePrice).setScale(3, RoundingMode.HALF_UP);
        }

        // Frais d'occupation (idle fee)
        long idleMinutes = 0L;
        BigDecimal idleFee = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

        if (chargeCompletedAt != null && stoppedAt != null && stoppedAt.isAfter(chargeCompletedAt)) {
            idleMinutes = Duration.between(chargeCompletedAt, stoppedAt).toMinutes();
            long billableMinutes = Math.max(0L, idleMinutes - gracePeriodMinutes);
            if (billableMinutes > 0 && station.getIdleFeePerMinute() != null) {
                idleFee = station.getIdleFeePerMinute().multiply(BigDecimal.valueOf(billableMinutes)).setScale(3, RoundingMode.HALF_UP);
            }
        }

        BigDecimal totalAmount = energyCost.add(idleFee).setScale(3, RoundingMode.HALF_UP);

        return new CalculationResult(
                safeTotalKwh.setScale(3, RoundingMode.HALF_UP),
                safePeakKwh.setScale(3, RoundingMode.HALF_UP),
                safeOffPeakKwh.setScale(3, RoundingMode.HALF_UP),
                peakCost,
                offPeakCost,
                energyCost,
                idleMinutes,
                idleFee,
                totalAmount
        );
    }

    public CalculationResult calculateInvoice(
            Station station,
            BigDecimal totalEnergyKwh,
            BigDecimal peakKwh,
            BigDecimal offPeakKwh,
            Instant chargeCompletedAt,
            Instant stoppedAt) {
        return calculateInvoice(station, totalEnergyKwh, peakKwh, offPeakKwh, chargeCompletedAt, stoppedAt, DEFAULT_GRACE_PERIOD_MINUTES);
    }
}
