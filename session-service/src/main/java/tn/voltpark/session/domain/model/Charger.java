package tn.voltpark.session.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "chargers")
public class Charger {

    @Id
    private String id; // ex: CP-460375-1

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    @JsonIgnore
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerType chargerType = ChargerType.SOAP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerStatus status = ChargerStatus.AVAILABLE;

    private Double maxPowerKw;

    /** Connecteur : CCS, CHAdeMO ou Type 2. */
    @Column(length = 20)
    private String connectorType;

    public Charger() {
    }

    public Charger(String id, Station station, ChargerType chargerType, ChargerStatus status, Double maxPowerKw) {
        this.id = id;
        this.station = station;
        this.chargerType = chargerType;
        this.status = status;
        this.maxPowerKw = maxPowerKw;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public ChargerType getChargerType() {
        return chargerType;
    }

    public void setChargerType(ChargerType chargerType) {
        this.chargerType = chargerType;
    }

    public ChargerStatus getStatus() {
        return status;
    }

    public void setStatus(ChargerStatus status) {
        this.status = status;
    }

    public Double getMaxPowerKw() {
        return maxPowerKw;
    }

    public void setMaxPowerKw(Double maxPowerKw) {
        this.maxPowerKw = maxPowerKw;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public enum ChargerType {
        SOAP,
        OCPI,
        STANDARD
    }

    public enum ChargerStatus {
        AVAILABLE,
        OCCUPIED,
        CHARGING,
        FAULTED,
        UNAVAILABLE
    }
}
