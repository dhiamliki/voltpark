package tn.voltpark.session.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "charging_sessions")
public class ChargingSession {

    @Id
    private String id; // UUID string

    @Column(nullable = false)
    private Long driverId;

    @Column(nullable = false)
    private String chargerId;

    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.PENDING_START;

    private Integer transactionId;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant stoppedAt;

    private Instant chargeCompletedAt;

    @Column(precision = 10, scale = 3)
    private BigDecimal totalEnergyKwh = BigDecimal.ZERO;

    @Column(precision = 10, scale = 3)
    private BigDecimal peakKwh = BigDecimal.ZERO;

    @Column(precision = 10, scale = 3)
    private BigDecimal offPeakKwh = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ChargingSession() {
    }

    public ChargingSession(Long driverId, String chargerId, Long reservationId) {
        this.id = UUID.randomUUID().toString();
        this.driverId = driverId;
        this.chargerId = chargerId;
        this.reservationId = reservationId;
        this.status = SessionStatus.PENDING_START;
        this.startedAt = Instant.now();
        this.createdAt = Instant.now();
        this.totalEnergyKwh = BigDecimal.ZERO;
        this.peakKwh = BigDecimal.ZERO;
        this.offPeakKwh = BigDecimal.ZERO;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getChargerId() {
        return chargerId;
    }

    public void setChargerId(String chargerId) {
        this.chargerId = chargerId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Integer getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Integer transactionId) {
        this.transactionId = transactionId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public Instant getChargeCompletedAt() {
        return chargeCompletedAt;
    }

    public void setChargeCompletedAt(Instant chargeCompletedAt) {
        this.chargeCompletedAt = chargeCompletedAt;
    }

    public BigDecimal getTotalEnergyKwh() {
        return totalEnergyKwh;
    }

    public void setTotalEnergyKwh(BigDecimal totalEnergyKwh) {
        this.totalEnergyKwh = totalEnergyKwh;
    }

    public BigDecimal getPeakKwh() {
        return peakKwh;
    }

    public void setPeakKwh(BigDecimal peakKwh) {
        this.peakKwh = peakKwh;
    }

    public BigDecimal getOffPeakKwh() {
        return offPeakKwh;
    }

    public void setOffPeakKwh(BigDecimal offPeakKwh) {
        this.offPeakKwh = offPeakKwh;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum SessionStatus {
        PENDING_START,
        ACTIVE,
        STOPPED,
        COMPLETED,
        FAILED
    }
}
