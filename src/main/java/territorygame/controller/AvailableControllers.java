package territorygame.controller;

import candidate.CandidateController;
import candidate.examples.BasicStateMachine;
import candidate.examples.RandomStateMachine;
import territorygame.api.AgentController;

import java.util.List;
import java.util.function.Supplier;

/** Registry of controller implementations the GUI offers per player slot. */
public final class AvailableControllers {

    public record ControllerOption(String label, Supplier<AgentController> factory) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static final List<ControllerOption> ALL = List.of(
            new ControllerOption("Basic State Machine", BasicStateMachine::new),
            new ControllerOption("Enemy State Machine", EnemyStateMachine::new),
            new ControllerOption("Random State Machine", RandomStateMachine::new),
            new ControllerOption("Candidate Controller", CandidateController::new)
    );

    private AvailableControllers() {
    }
}
