package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.helpers.MovementUtils;

import java.util.List;
import java.util.Random;

/**
 * Simplest baseline controller: picks uniformly among directions that are
 * mechanically valid (in bounds, not onto the opponent's agent). Does not
 * avoid its own trail, so it can legitimately kill itself. It isn't a
 * state machine — no persistent decision state, just a random pick each turn.
 */
public final class RandomAgentController implements AgentController {

    private final Random random = new Random(7);

    @Override
    public void takeTurn(GameApi game) {
        List<Direction> validDirections = MovementUtils.validDirections(game);

        Direction choice;
        if (validDirections.isEmpty()) {
            choice = MovementUtils.randomDirection(random);
        } else {
            choice = validDirections.get(random.nextInt(validDirections.size()));
        }

        game.move(choice);
    }
}
