package tn.voltpark.charger.model;

import jakarta.persistence.*;
import tn.voltpark.charger.ws.ChargerStatus;

import java.time.Instant;

@Entity
@Table(name = "chargers")
public class ChargerStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String chargerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerStatus status;

    private Integer currentTransactionId;

    private String currentIdTag;

    private Integer connectorId;

    private Instant updatedAt;

    public ChargerStation() {
    }

    public ChargerStation(String chargerId, ChargerStatus status) {
        this.chargerId = chargerId;
        this.status = status;
        this.connectorId = 1;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getChargerId() {
        return chargerId;
    }

    public void setChargerId(String chargerId) {
        this.chargerId = chargerId;
    }

    public ChargerStatus getStatus() {
        return status;
    }

    public void setStatus(ChargerStatus status) {
        this.status = status;
    }

    public Integer getCurrentTransactionId() {
        return currentTransactionId;
    }

    public void setCurrentTransactionId(Integer currentTransactionId) {
        this.currentTransactionId = currentTransactionId;
    }

    public String getCurrentIdTag() {
        return currentIdTag;
    }

    public void setCurrentIdTag(String currentIdTag) {
        this.currentIdTag = currentIdTag;
    }

    public Integer getConnectorId() {
        return connectorId;
    }

    public void setConnectorId(Integer connectorId) {
        this.connectorId = connectorId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
