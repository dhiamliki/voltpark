package tn.voltpark.charger.service;

/** RemoteStopTransaction avec un transactionId qui n'est pas la transaction en cours de la borne. */
public class InvalidTransactionException extends RuntimeException {
    private final String chargerId;
    private final int transactionId;
    private final Integer currentTransactionId;

    public InvalidTransactionException(String chargerId, int transactionId, Integer currentTransactionId) {
        super("Transaction " + transactionId + " inconnue sur la borne " + chargerId
                + (currentTransactionId != null ? " (transaction en cours: " + currentTransactionId + ")" : " (aucune transaction en cours)"));
        this.chargerId = chargerId;
        this.transactionId = transactionId;
        this.currentTransactionId = currentTransactionId;
    }

    public String getChargerId() {
        return chargerId;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public Integer getCurrentTransactionId() {
        return currentTransactionId;
    }
}
