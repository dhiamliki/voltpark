package tn.voltpark.session.api.error;

public class ChargerCommunicationException extends RuntimeException {
    public ChargerCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChargerCommunicationException(String message) {
        super(message);
    }
}
