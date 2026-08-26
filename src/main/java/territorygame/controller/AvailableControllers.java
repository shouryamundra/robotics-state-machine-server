package territorygame.controller;

import candidate.CandidateController;
import candidate.examples.ExampleAgentController;
import candidate.examples.RandomAgentController;
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
            new ControllerOption("Example Agent", ExampleAgentController::new),
            new ControllerOption("Provided Bot", ProvidedBotController::new),
            new ControllerOption("Random Agent", RandomAgentController::new),
            new ControllerOption("Candidate Controller", CandidateController::new)
    );

    private AvailableControllers() {
    }
}
