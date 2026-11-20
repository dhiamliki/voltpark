package tn.voltpark.charger.model;

import jakarta.persistence.*;

@Entity
@Table(name = "authorized_badges")
public class AuthorizedBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idTag;

    private String driverId;

    @Column(nullable = false)
    private boolean active;

    public AuthorizedBadge() {
    }

    public AuthorizedBadge(String idTag, String driverId, boolean active) {
        this.idTag = idTag;
        this.driverId = driverId;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getIdTag() {
        return idTag;
    }

    public void setIdTag(String idTag) {
        this.idTag = idTag;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
