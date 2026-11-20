package tn.voltpark.energy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.voltpark.energy.config.TariffProperties;
import tn.voltpark.energy.model.MeterReading;
import tn.voltpark.energy.model.SessionEnergy;
import tn.voltpark.energy.repository.MeterReadingRepository;
import tn.voltpark.energy.repository.SessionEnergyRepository;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnergyServiceTest {

    private static final String SESSION = "S-1";
    private static final String CHARGER = "CP-1";

    /** Sessions "enregistrees" par le repository simule. */
    private final Map<String, SessionEnergy> sessions = new HashMap<>();

    private EnergyService service;

    @BeforeEach
    void setUp() {
        SessionEnergyRepository sessionRepository = mock(SessionEnergyRepository.class);
        when(sessionRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(sessions.get(inv.<String>getArgument(0))));
        when(sessionRepository.save(any(SessionEnergy.class))).thenAnswer(inv -> {
            SessionEnergy session = inv.getArgument(0);
            sessions.put(session.getSessionId(), session);
            return session;
        });

        MeterReadingRepository readingRepository = mock(MeterReadingRepository.class);
        when(readingRepository.save(any(MeterReading.class))).thenAnswer(inv -> inv.getArgument(0));

        TariffProperties tariff = new TariffProperties(
                LocalTime.of(8, 0), LocalTime.of(20, 0), ZoneId.of("Africa/Tunis"));
        service = new EnergyService(readingRepository, sessionRepository, tariff);
    }

    @Test
    void splitsEnergyBetweenPeakAndOffPeakAroundEightPm() {
        // Africa/Tunis = UTC+1 : 18:59Z = 19:59 locale (pleines), 19:01Z = 20:01 locale (creuses)
        service.recordReading(reading("2026-03-10T18:59:00Z", 2.0, 40));
        service.recordReading(reading("2026-03-10T18:59:50Z", 3.0, 42));
        SessionEnergy session = service.recordReading(reading("2026-03-10T19:01:00Z", 4.5, 45));

        assertThat(session.getPeakKwh()).isCloseTo(3.0, within(1e-9));
        assertThat(session.getOffPeakKwh()).isCloseTo(1.5, within(1e-9));
        assertThat(session.getTotalEnergyKwh()).isCloseTo(4.5, within(1e-9));
        assertThat(session.getReadingsCount()).isEqualTo(3);
    }

    @Test
    void rejectsDecreasingEnergy() {
        service.recordReading(reading("2026-03-10T10:00:00Z", 5.0, 50));

        assertThatThrownBy(() -> service.recordReading(reading("2026-03-10T10:00:01Z", 4.9, 50)))
                .isInstanceOf(InvalidReadingException.class)
                .hasMessageContaining("lower than the previous reading");
    }

    @Test
    void rejectsNegativePower() {
        NewReading negative = new NewReading(SESSION, CHARGER, Instant.parse("2026-03-10T10:00:00Z"), -1.0, 1.0, 50);

        assertThatThrownBy(() -> service.recordReading(negative))
                .isInstanceOf(InvalidReadingException.class);
    }

    @Test
    void rejectsSocOutOfRange() {
        assertThatThrownBy(() -> service.recordReading(reading("2026-03-10T10:00:00Z", 1.0, 101)))
                .isInstanceOf(InvalidReadingException.class);
        assertThatThrownBy(() -> service.recordReading(reading("2026-03-10T10:00:00Z", 1.0, -1)))
                .isInstanceOf(InvalidReadingException.class);
    }

    @Test
    void rejectsReadingForClosedSession() {
        service.recordReading(reading("2026-03-10T10:00:00Z", 1.0, 50));
        service.closeSession(SESSION);

        assertThatThrownBy(() -> service.recordReading(reading("2026-03-10T10:00:05Z", 1.2, 51)))
                .isInstanceOf(SessionClosedException.class);
    }

    @Test
    void unknownSessionIsNotFound() {
        assertThatThrownBy(() -> service.getSessionEnergy("UNKNOWN"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void estimatesMinutesToFullFromSocSlope() {
        // +10 % en 5 minutes => 2 %/min ; il reste 40 % => 20 minutes
        List<MeterReading> readings = List.of(
                meter("2026-03-10T10:05:00Z", 60),
                meter("2026-03-10T10:00:00Z", 50));

        assertThat(service.estimateMinutesRemaining(readings)).isEqualTo(20);
    }

    @Test
    void estimateIsMinusOneWhenNotPossible() {
        assertThat(service.estimateMinutesRemaining(List.of(meter("2026-03-10T10:00:00Z", 50))))
                .isEqualTo(-1);
        assertThat(service.estimateMinutesRemaining(List.of(
                meter("2026-03-10T10:00:00Z", 50),
                meter("2026-03-10T10:05:00Z", 50))))
                .isEqualTo(-1);
    }

    private static NewReading reading(String time, double energyKwh, int soc) {
        return new NewReading(SESSION, CHARGER, Instant.parse(time), 11.0, energyKwh, soc);
    }

    private static MeterReading meter(String time, int soc) {
        return new MeterReading(SESSION, CHARGER, Instant.parse(time), 11.0, 0.0, soc);
    }
}
