package tn.voltpark.session.api.error;

/** La ressource existe deja (email ou badge deja utilise...). Traitee en 409. */
public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) {
        super(message);
    }
}
