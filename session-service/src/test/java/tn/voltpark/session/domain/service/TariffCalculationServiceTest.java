package tn.voltpark.session.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.voltpark.session.domain.model.Station;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TariffCalculationServiceTest {

    private TariffCalculationService service;
    private Station station;

    @BeforeEach
    void setUp() {
        service = new TariffCalculationService();
        station = new Station(
                "S1",
                "Lac 1",
                36.8,
                10.2,
                new BigDecimal("0.400"),
                new BigDecimal("0.500"),
                new BigDecimal("0.300"),
                new BigDecimal("0.100")
        );
    }

    @Test
    void testStandardRateCalculation() {
        BigDecimal energy = new BigDecimal("20.000");
        TariffCalculationService.CalculationResult r = service.calculateInvoice(
                station, energy, BigDecimal.ZERO, BigDecimal.ZERO, null, null);

        assertEquals(new BigDecimal("8.000"), r.energyCost());
        assertEquals(new BigDecimal("0.000"), r.idleFee());
        assertEquals(new BigDecimal("8.000"), r.totalAmount());
    }

    @Test
    void testPeakAndOffPeakRateCalculation() {
        BigDecimal peak = new BigDecimal("10.000");
        BigDecimal offPeak = new BigDecimal("20.000");
        BigDecimal total = new BigDecimal("30.000");

        TariffCalculationService.CalculationResult r = service.calculateInvoice(
                station, total, peak, offPeak, null, null);

        assertEquals(new BigDecimal("5.000"), r.peakEnergyCost());
        assertEquals(new BigDecimal("6.000"), r.offPeakEnergyCost());
        assertEquals(new BigDecimal("11.000"), r.energyCost());
        assertEquals(new BigDecimal("11.000"), r.totalAmount());
    }

    @Test
    void testGracePeriodNoIdleFee() {
        Instant completed = Instant.now();
        Instant stopped = completed.plus(10, ChronoUnit.MINUTES);

        TariffCalculationService.CalculationResult r = service.calculateInvoice(
                station, new BigDecimal("10.000"), null, null, completed, stopped);

        assertEquals(new BigDecimal("0.000"), r.idleFee());
        assertEquals(new BigDecimal("4.000"), r.totalAmount());
    }

    @Test
    void testExceededGracePeriodComputesFee() {
        Instant completed = Instant.now();
        Instant stopped = completed.plus(45, ChronoUnit.MINUTES);

        TariffCalculationService.CalculationResult r = service.calculateInvoice(
                station, new BigDecimal("10.000"), null, null, completed, stopped);

        assertEquals(new BigDecimal("3.000"), r.idleFee());
        assertEquals(new BigDecimal("7.000"), r.totalAmount());
    }
}
