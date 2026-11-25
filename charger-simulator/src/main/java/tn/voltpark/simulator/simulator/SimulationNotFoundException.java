package tn.voltpark.simulator.simulator;

public class SimulationNotFoundException extends RuntimeException {

    public SimulationNotFoundException(String sessionId) {
        super("No running simulation for session " + sessionId);
    }
}
