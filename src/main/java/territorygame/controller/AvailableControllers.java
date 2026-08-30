package territorygame.controller;

import candidate.CandidateController;
import candidate.examples.BasicStateMachine;
import candidate.examples.RandomStateMachine;
import candidate.examples.SafetyGridStateMachine;
import territorygame.api.AgentController;

import java.util.List;
import java.util.function.Function;

/** Registry of controller implementations the GUI offers per player slot. */
public final class AvailableControllers {

    /**
     * The factory takes the player slot's configured random seed
     * ({@link territorygame.domain.GameConfig#controllerSeeds()}); most
     * controllers have no randomness of their own and just ignore it.
     */
    public record ControllerOption(String label, Function<Long, AgentController> factory) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static final List<ControllerOption> ALL = List.of(
            new ControllerOption("Basic State Machine", seed -> new BasicStateMachine()),
            new ControllerOption("Safety Grid State Machine", seed -> new SafetyGridStateMachine()),
            new ControllerOption("Enemy State Machine", EnemyStateMachine::new),
            new ControllerOption("Random State Machine", seed -> new RandomStateMachine()),
            new ControllerOption("Candidate Controller", seed -> new CandidateController())
    );

    private AvailableControllers() {
    }
}
