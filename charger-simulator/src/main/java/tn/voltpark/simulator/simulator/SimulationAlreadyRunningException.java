package tn.voltpark.simulator.simulator;

public class SimulationAlreadyRunningException extends RuntimeException {

    public SimulationAlreadyRunningException(String sessionId) {
        super("A simulation is already running for session " + sessionId);
    }
}
