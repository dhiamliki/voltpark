package tn.voltpark.energy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Bilan energetique d'une session (une ligne par session).
 * Mis a jour a chaque nouvelle mesure, ce qui evite de relire toutes les mesures
 * pour repondre a GetSessionEnergy.
 */
@Entity
@Table(name = "session_energy")
public class SessionEnergy {

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "charger_id", nullable = false, length = 64)
    private String chargerId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_reading_at", nullable = false)
    private Instant lastReadingAt;

    @Column(name = "last_energy_kwh", nullable = false)
    private double lastEnergyKwh;

    @Column(name = "peak_kwh", nullable = false)
    private double peakKwh;

    @Column(name = "off_peak_kwh", nullable = false)
    private double offPeakKwh;

    @Column(name = "readings_count", nullable = false)
    private long readingsCount;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    protected SessionEnergy() {
        // requis par JPA
    }

    public static SessionEnergy open(String sessionId, String chargerId, Instant startedAt) {
        SessionEnergy session = new SessionEnergy();
        session.sessionId = sessionId;
        session.chargerId = chargerId;
        session.startedAt = startedAt;
        session.lastReadingAt = startedAt;
        return session;
    }

    /**
     * Ajoute une mesure : l'energie consommee depuis la mesure precedente est
     * comptee en heures pleines ou en heures creuses.
     */
    public void addReading(Instant timestamp, double energyKwh, boolean peak) {
        double delta = energyKwh - lastEnergyKwh;
        if (peak) {
            peakKwh += delta;
        } else {
            offPeakKwh += delta;
        }
        lastEnergyKwh = energyKwh;
        lastReadingAt = timestamp;
        readingsCount++;
    }

    public void close() {
        closed = true;
    }

    public double getTotalEnergyKwh() {
        return lastEnergyKwh;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getChargerId() {
        return chargerId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastReadingAt() {
        return lastReadingAt;
    }

    public double getLastEnergyKwh() {
        return lastEnergyKwh;
    }

    public double getPeakKwh() {
        return peakKwh;
    }

    public double getOffPeakKwh() {
        return offPeakKwh;
    }

    public long getReadingsCount() {
        return readingsCount;
    }

    public boolean isClosed() {
        return closed;
    }
}
