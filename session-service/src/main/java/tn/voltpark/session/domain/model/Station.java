package tn.voltpark.session.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stations")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code stable de la station, ex: ST-OCM-460375 (identifiant Open Charge Map), null si creee par l'API. */
    @Column(unique = true, length = 40)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Operateur reel (Open Charge Map), null s'il n'est pas connu. */
    private String operator;

    /** Corridor autoroutier, ex: "A1", null hors corridor. */
    @Column(length = 10)
    private String corridor;

    private String address;

    private Double latitude;

    private Double longitude;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal pricePerKwh;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal peakPricePerKwh;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal offPeakPricePerKwh;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal idleFeePerMinute;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Charger> chargers = new ArrayList<>();

    public Station() {
    }

    public Station(String name, String address, Double latitude, Double longitude,
                   BigDecimal pricePerKwh, BigDecimal peakPricePerKwh,
                   BigDecimal offPeakPricePerKwh, BigDecimal idleFeePerMinute) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pricePerKwh = pricePerKwh;
        this.peakPricePerKwh = peakPricePerKwh;
        this.offPeakPricePerKwh = offPeakPricePerKwh;
        this.idleFeePerMinute = idleFeePerMinute;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getCorridor() {
        return corridor;
    }

    public void setCorridor(String corridor) {
        this.corridor = corridor;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getPricePerKwh() {
        return pricePerKwh;
    }

    public void setPricePerKwh(BigDecimal pricePerKwh) {
        this.pricePerKwh = pricePerKwh;
    }

    public BigDecimal getPeakPricePerKwh() {
        return peakPricePerKwh;
    }

    public void setPeakPricePerKwh(BigDecimal peakPricePerKwh) {
        this.peakPricePerKwh = peakPricePerKwh;
    }

    public BigDecimal getOffPeakPricePerKwh() {
        return offPeakPricePerKwh;
    }

    public void setOffPeakPricePerKwh(BigDecimal offPeakPricePerKwh) {
        this.offPeakPricePerKwh = offPeakPricePerKwh;
    }

    public BigDecimal getIdleFeePerMinute() {
        return idleFeePerMinute;
    }

    public void setIdleFeePerMinute(BigDecimal idleFeePerMinute) {
        this.idleFeePerMinute = idleFeePerMinute;
    }

    public List<Charger> getChargers() {
        return chargers;
    }

    public void setChargers(List<Charger> chargers) {
        this.chargers = chargers;
    }
}
