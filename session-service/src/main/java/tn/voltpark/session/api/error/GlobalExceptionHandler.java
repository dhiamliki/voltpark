package tn.voltpark.session.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Toutes les erreurs au format ProblemDetail (RFC 7807).
 *
 * ResponseEntityExceptionHandler traite les erreurs propres a Spring MVC avec le bon statut :
 * JSON illisible (400), parametre du mauvais type comme /drivers/abc (400), URL inconnue (404),
 * methode non supportee (405), type de contenu non supporte (415)...
 * Le handler Exception.class ne recoit donc plus que les vraies erreurs internes (500).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Ressource introuvable");
        problem.setType(URI.create("https://voltpark.tn/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail handleResourceConflict(ResourceConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Ressource deja existante");
        problem.setType(URI.create("https://voltpark.tn/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ProblemDetail handleReservationConflict(ReservationConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflit de reservation");
        problem.setType(URI.create("https://voltpark.tn/errors/reservation-conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Demarrage sans reservation refuse (borne occupee, hors service ou reservee) : 409 avec errorCode. */
    @ExceptionHandler(ChargerUnavailableException.class)
    public ProblemDetail handleChargerUnavailable(ChargerUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Borne indisponible");
        problem.setType(URI.create("https://voltpark.tn/errors/charger-unavailable"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidSessionStateException.class)
    public ProblemDetail handleInvalidSessionState(InvalidSessionStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Etat de session invalide");
        problem.setType(URI.create("https://voltpark.tn/errors/invalid-session-state"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** SOAP Fault de la borne : statut choisi selon le code d'erreur (404, 409 ou 403). */
    @ExceptionHandler(ChargerFaultException.class)
    public ProblemDetail handleChargerFault(ChargerFaultException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle("Demarrage refuse par la borne (SOAP Fault)");
        problem.setType(URI.create("https://voltpark.tn/errors/charger-fault"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ChargerCommunicationException.class)
    public ProblemDetail handleChargerCommunication(ChargerCommunicationException ex) {
        log.warn("Charger communication error: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Erreur de passerelle borne (SOAP)");
        problem.setType(URI.create("https://voltpark.tn/errors/bad-gateway"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(EnergyUnavailableException.class)
    public ProblemDetail handleEnergyUnavailable(EnergyUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Service energie indisponible (gRPC)");
        problem.setType(URI.create("https://voltpark.tn/errors/energy-unavailable"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Erreurs de validation (@Valid) : 400 avec la liste des champs invalides. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Donnees de requete invalides");
        problem.setTitle("Erreur de validation");
        problem.setType(URI.create("https://voltpark.tn/errors/validation-error"));
        problem.setProperty("timestamp", Instant.now());

        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("invalidParams", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Ajoute l'horodatage aux ProblemDetail produits par Spring (400, 404, 405, 415...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp"))) {
            problem.setProperty("timestamp", Instant.now());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requete invalide");
        problem.setType(URI.create("https://voltpark.tn/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled internal server error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne inattendue est survenue");
        problem.setTitle("Erreur interne du serveur");
        problem.setType(URI.create("https://voltpark.tn/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
