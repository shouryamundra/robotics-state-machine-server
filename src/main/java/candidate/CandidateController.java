package candidate;

// Replace this stub with your own implementation. See CANDIDATE_GUIDE.md
// for the game rules, the GameApi surface, and the helpers you're given.

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.helpers.MovementUtils;

public final class CandidateController implements AgentController {

    @Override
    public void takeTurn(GameApi game) {
        if (MovementUtils.isValidMove(game, Direction.EAST)) {
            game.move(Direction.EAST);
        } else {
            game.move(Direction.NORTH);
        }
    }
}
