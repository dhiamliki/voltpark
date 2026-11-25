package tn.voltpark.session.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false)
    private Long driverId;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal totalEnergyKwh;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal peakKwh;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal offPeakKwh;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal peakEnergyCost;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal offPeakEnergyCost;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal energyCost;

    private Long idleDurationMinutes;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal idleFee;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency = "TND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    public Invoice() {
    }

    public Invoice(String sessionId, Long driverId, BigDecimal totalEnergyKwh,
                   BigDecimal peakKwh, BigDecimal offPeakKwh,
                   BigDecimal peakEnergyCost, BigDecimal offPeakEnergyCost,
                   BigDecimal energyCost, Long idleDurationMinutes,
                   BigDecimal idleFee, BigDecimal totalAmount, String currency) {
        this.sessionId = sessionId;
        this.driverId = driverId;
        this.totalEnergyKwh = totalEnergyKwh;
        this.peakKwh = peakKwh;
        this.offPeakKwh = offPeakKwh;
        this.peakEnergyCost = peakEnergyCost;
        this.offPeakEnergyCost = offPeakEnergyCost;
        this.energyCost = energyCost;
        this.idleDurationMinutes = idleDurationMinutes;
        this.idleFee = idleFee;
        this.totalAmount = totalAmount;
        this.currency = currency != null ? currency : "TND";
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public BigDecimal getTotalEnergyKwh() {
        return totalEnergyKwh;
    }

    public BigDecimal getPeakKwh() {
        return peakKwh;
    }

    public BigDecimal getOffPeakKwh() {
        return offPeakKwh;
    }

    public BigDecimal getPeakEnergyCost() {
        return peakEnergyCost;
    }

    public BigDecimal getOffPeakEnergyCost() {
        return offPeakEnergyCost;
    }

    public BigDecimal getEnergyCost() {
        return energyCost;
    }

    public Long getIdleDurationMinutes() {
        return idleDurationMinutes;
    }

    public BigDecimal getIdleFee() {
        return idleFee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    /** Date d'emission (donnees de demonstration : factures passees). */
    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    /**
     * Remplace les montants (recalcul d'une facture PENDING_ENERGY) et emet la facture.
     */
    public void applyCalculation(BigDecimal totalEnergyKwh, BigDecimal peakKwh, BigDecimal offPeakKwh,
                                 BigDecimal peakEnergyCost, BigDecimal offPeakEnergyCost,
                                 BigDecimal energyCost, Long idleDurationMinutes,
                                 BigDecimal idleFee, BigDecimal totalAmount) {
        this.totalEnergyKwh = totalEnergyKwh;
        this.peakKwh = peakKwh;
        this.offPeakKwh = offPeakKwh;
        this.peakEnergyCost = peakEnergyCost;
        this.offPeakEnergyCost = offPeakEnergyCost;
        this.energyCost = energyCost;
        this.idleDurationMinutes = idleDurationMinutes;
        this.idleFee = idleFee;
        this.totalAmount = totalAmount;
        this.status = InvoiceStatus.ISSUED;
    }

    public enum InvoiceStatus {
        // energie indisponible a l'arret (energy-service injoignable) : montants a recalculer
        PENDING_ENERGY,
        ISSUED,
        PAID,
        CANCELLED
    }
}
