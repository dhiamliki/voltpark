package tn.voltpark.session.api.error;

/** energy-service injoignable (apres les nouveaux essais) ou circuit ouvert. Traitee en 503. */
public class EnergyUnavailableException extends RuntimeException {
    public EnergyUnavailableException(String message) {
        super(message);
    }
}
