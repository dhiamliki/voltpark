package tn.voltpark.session.client.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.function.Predicate;

/**
 * Erreurs gRPC qui justifient un nouvel essai : energy-service injoignable (UNAVAILABLE)
 * ou trop lent (DEADLINE_EXCEEDED). NOT_FOUND ou INVALID_ARGUMENT ne changeront pas en reessayant.
 */
public class RetryableEnergyError implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
        }
        return false;
    }
}
