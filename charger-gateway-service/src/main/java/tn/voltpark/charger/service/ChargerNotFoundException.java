package tn.voltpark.charger.service;

public class ChargerNotFoundException extends RuntimeException {
    private final String chargerId;

    public ChargerNotFoundException(String chargerId) {
        super("Borne inconnue: " + chargerId);
        this.chargerId = chargerId;
    }

    public String getChargerId() {
        return chargerId;
    }
}
