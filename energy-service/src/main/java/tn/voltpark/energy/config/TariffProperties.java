package tn.voltpark.energy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Periodes tarifaires (heures pleines / heures creuses).
 * Valeurs lues dans application.yml, surchargeables par PEAK_START, PEAK_END et TARIFF_ZONE.
 */
@ConfigurationProperties(prefix = "voltpark.tariff")
public record TariffProperties(
        @DateTimeFormat(pattern = "HH:mm") LocalTime peakStart,
        @DateTimeFormat(pattern = "HH:mm") LocalTime peakEnd,
        ZoneId zone) {

    /** Indique si l'instant donne tombe en heures pleines, selon l'heure locale de la zone. */
    public boolean isPeak(Instant instant) {
        LocalTime local = instant.atZone(zone).toLocalTime();
        if (peakStart.isBefore(peakEnd)) {
            return !local.isBefore(peakStart) && local.isBefore(peakEnd);
        }
        // periode qui passe minuit, par exemple 22:00 -> 06:00
        return !local.isBefore(peakStart) || local.isBefore(peakEnd);
    }
}
