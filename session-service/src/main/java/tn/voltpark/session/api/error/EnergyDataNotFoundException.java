package tn.voltpark.session.api.error;

/** energy-service n'a aucune mesure pour cette session (gRPC NOT_FOUND). Traitee en 404. */
public class EnergyDataNotFoundException extends ResourceNotFoundException {
    public EnergyDataNotFoundException(String sessionId) {
        super("Aucune mesure d'energie pour la session " + sessionId);
    }
}
