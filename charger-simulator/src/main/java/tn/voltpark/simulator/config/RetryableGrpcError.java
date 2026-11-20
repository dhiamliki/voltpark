package tn.voltpark.simulator.config;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.function.Predicate;

/**
 * Decide quelles erreurs justifient un nouvel essai : seulement UNAVAILABLE
 * (energy-service arrete ou injoignable). Une erreur de validation ne sera pas
 * corrigee en reessayant.
 */
public class RetryableGrpcError implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof StatusRuntimeException e
                && e.getStatus().getCode() == Status.Code.UNAVAILABLE;
    }
}
