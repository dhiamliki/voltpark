package tn.voltpark.energy.service;

/** Aucune mesure n'a jamais ete recue pour cette session. */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("Unknown session " + sessionId);
    }
}
