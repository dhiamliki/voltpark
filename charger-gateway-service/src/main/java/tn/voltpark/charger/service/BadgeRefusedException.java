package tn.voltpark.charger.service;

public class BadgeRefusedException extends RuntimeException {
    private final String idTag;
    private final String reason;

    public BadgeRefusedException(String idTag, String reason) {
        super("Badge refuse: " + idTag + " (" + reason + ")");
        this.idTag = idTag;
        this.reason = reason;
    }

    public String getIdTag() {
        return idTag;
    }

    public String getReason() {
        return reason;
    }
}
