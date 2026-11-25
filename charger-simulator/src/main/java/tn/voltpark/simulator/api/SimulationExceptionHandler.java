package tn.voltpark.simulator.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tn.voltpark.simulator.simulator.SimulationAlreadyRunningException;
import tn.voltpark.simulator.simulator.SimulationNotFoundException;

/** Erreurs de l'API au format ProblemDetail ; les erreurs de validation (400) viennent de la classe parente. */
@RestControllerAdvice
public class SimulationExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SimulationNotFoundException.class)
    public ProblemDetail handleNotFound(SimulationNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SimulationAlreadyRunningException.class)
    public ProblemDetail handleConflict(SimulationAlreadyRunningException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
