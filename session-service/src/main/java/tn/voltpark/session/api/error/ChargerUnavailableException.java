package tn.voltpark.session.api.error;

/**
 * Demarrage sans reservation refuse avant tout appel a la borne : borne occupee, hors service
 * ou reservee par un autre conducteur dans les 30 prochaines minutes (409 avec errorCode).
 */
public class ChargerUnavailableException extends RuntimeException {

    public static final String OCCUPIED = "CHARGER_OCCUPIED";
    public static final String OUT_OF_SERVICE = "CHARGER_OUT_OF_SERVICE";
    public static final String RESERVED = "CHARGER_RESERVED";

    private final String errorCode;

    public ChargerUnavailableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
