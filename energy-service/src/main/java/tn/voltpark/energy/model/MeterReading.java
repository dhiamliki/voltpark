package tn.voltpark.energy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Une mesure envoyee par une borne pendant une session de charge.
 * L'energie est cumulee depuis le debut de la session.
 */
@Entity
@Table(name = "meter_reading",
        indexes = @Index(name = "idx_meter_reading_session_time", columnList = "session_id, reading_time"))
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "charger_id", nullable = false, length = 64)
    private String chargerId;

    @Column(name = "reading_time", nullable = false)
    private Instant timestamp;

    @Column(name = "power_kw", nullable = false)
    private double powerKw;

    @Column(name = "energy_kwh", nullable = false)
    private double energyKwh;

    @Column(name = "soc_percent", nullable = false)
    private int socPercent;

    protected MeterReading() {
        // requis par JPA
    }

    public MeterReading(String sessionId, String chargerId, Instant timestamp,
                        double powerKw, double energyKwh, int socPercent) {
        this.sessionId = sessionId;
        this.chargerId = chargerId;
        this.timestamp = timestamp;
        this.powerKw = powerKw;
        this.energyKwh = energyKwh;
        this.socPercent = socPercent;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getChargerId() {
        return chargerId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public double getPowerKw() {
        return powerKw;
    }

    public double getEnergyKwh() {
        return energyKwh;
    }

    public int getSocPercent() {
        return socPercent;
    }
}
