package tn.voltpark.energy.service;

/** Mesure recue pour une session deja cloturee. */
public class SessionClosedException extends RuntimeException {

    public SessionClosedException(String sessionId) {
        super("Session " + sessionId + " is already closed");
    }
}
