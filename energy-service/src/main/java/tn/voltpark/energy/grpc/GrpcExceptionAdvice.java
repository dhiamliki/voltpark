package tn.voltpark.energy.grpc;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.voltpark.energy.service.InvalidReadingException;
import tn.voltpark.energy.service.SessionClosedException;
import tn.voltpark.energy.service.SessionNotFoundException;

/** Seul endroit ou les exceptions metier sont traduites en codes de statut gRPC. */
@GrpcAdvice
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    @GrpcExceptionHandler(InvalidReadingException.class)
    public Status handleInvalidReading(InvalidReadingException e) {
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public Status handleIllegalArgument(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
    }

    @GrpcExceptionHandler(SessionClosedException.class)
    public Status handleSessionClosed(SessionClosedException e) {
        return Status.FAILED_PRECONDITION.withDescription(e.getMessage());
    }

    @GrpcExceptionHandler(SessionNotFoundException.class)
    public Status handleSessionNotFound(SessionNotFoundException e) {
        return Status.NOT_FOUND.withDescription(e.getMessage());
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleUnexpected(Exception e) {
        log.error("Unexpected error in gRPC call", e);
        return Status.INTERNAL.withDescription("Internal error");
    }
}
