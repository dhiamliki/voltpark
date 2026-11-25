package tn.voltpark.charger.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Transaction de charge demarree par RemoteStartTransaction.
 * Son identifiant vient d'une sequence de la base : il ne repart pas a 1001 quand le service
 * redemarre, et plusieurs instances du service ne peuvent pas donner le meme numero.
 */
@Entity
@Table(name = "charger_transactions")
public class ChargerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charger_transaction_seq")
    @SequenceGenerator(name = "charger_transaction_seq", sequenceName = "charger_transaction_seq",
            initialValue = 1001, allocationSize = 1)
    private Integer id;

    @Column(nullable = false)
    private String chargerId;

    @Column(nullable = false)
    private String idTag;

    private String sessionId;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant stoppedAt;

    public ChargerTransaction() {
    }

    public ChargerTransaction(String chargerId, String idTag, String sessionId) {
        this.chargerId = chargerId;
        this.idTag = idTag;
        this.sessionId = sessionId;
        this.startedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public String getChargerId() {
        return chargerId;
    }

    public String getIdTag() {
        return idTag;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
