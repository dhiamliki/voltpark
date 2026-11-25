package tn.voltpark.session.api.error;

import org.springframework.http.HttpStatus;

/**
 * Reponse metier de la borne recue sous forme de SOAP Fault : borne inconnue (404),
 * borne occupee (409), badge refuse (403)...
 * Ce n'est pas une panne : jamais de nouvel essai, jamais compte par le circuit breaker,
 * jamais de PENDING_START.
 */
public class ChargerFaultException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ChargerFaultException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
