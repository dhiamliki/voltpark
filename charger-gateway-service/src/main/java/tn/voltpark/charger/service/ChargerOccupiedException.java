package tn.voltpark.charger.service;

public class ChargerOccupiedException extends RuntimeException {
    private final String chargerId;
    private final Integer currentTransactionId;

    public ChargerOccupiedException(String chargerId, Integer currentTransactionId) {
        super("Borne occupee: " + chargerId + (currentTransactionId != null ? " (transaction active: " + currentTransactionId + ")" : ""));
        this.chargerId = chargerId;
        this.currentTransactionId = currentTransactionId;
    }

    public String getChargerId() {
        return chargerId;
    }

    public Integer getCurrentTransactionId() {
        return currentTransactionId;
    }
}
