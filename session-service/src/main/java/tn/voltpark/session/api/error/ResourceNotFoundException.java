package tn.voltpark.session.api.error;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " introuvable avec l'identifiant: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
