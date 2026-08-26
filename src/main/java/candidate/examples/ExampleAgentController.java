package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.helpers.MovementUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deliberately weak reference implementation. It demonstrates persistent
 * controller state, state transitions, calling move(), and reacting to
 * MoveResult — not a strategy worth copying. On purpose, it does not avoid
 * its own trail: EXPANDING wanders randomly and RETURNING heads straight
 * back toward its respawn point, so it can still walk over its own trail
 * and die. See README.md's Tips section for ideas (avoiding your own
 * trail, finding your nearest territory, etc.) left undone here.
 */
public final class ExampleAgentController implements AgentController {

    private static final int RETURN_TRAIL_THRESHOLD = 4;

    private enum State {
        EXPANDING,
        RETURNING
    }

    private State state = State.EXPANDING;
    private final Random random = new Random(42);

    @Override
    public void takeTurn(GameApi game) {
        Direction direction;
        if (state == State.EXPANDING) {
            direction = pickExpandingDirection(game);
        } else {
            direction = pickReturningDirection(game);
        }

        MoveResult result = game.move(direction);
        updateState(game, result);
    }

    private void updateState(GameApi game, MoveResult result) {
        if (result == MoveResult.CAPTURED || result == MoveResult.DIED) {
            state = State.EXPANDING;
            return;
        }
        if (state == State.EXPANDING && game.getActiveTrail().size() >= RETURN_TRAIL_THRESHOLD) {
            state = State.RETURNING;
        } else if (state == State.RETURNING && game.getActiveTrail().isEmpty()) {
            state = State.EXPANDING;
        }
    }

    /** Wanders randomly among the mechanically safe directions. */
    private Direction pickExpandingDirection(GameApi game) {
        List<Direction> safeDirections = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (MovementUtils.isValidMove(game, direction)) {
                safeDirections.add(direction);
            }
        }
        if (safeDirections.isEmpty()) {
            return randomDirection();
        }
        return safeDirections.get(random.nextInt(safeDirections.size()));
    }

    /** Heads toward the respawn point one axis at a time; not necessarily the nearest owned cell. */
    private Direction pickReturningDirection(GameApi game) {
        GridPosition position = game.getAgentPosition();
        GridPosition home = game.getRespawnPosition();

        Direction preferred;
        if (position.x() < home.x()) {
            preferred = Direction.EAST;
        } else if (position.x() > home.x()) {
            preferred = Direction.WEST;
        } else if (position.y() < home.y()) {
            preferred = Direction.SOUTH;
        } else if (position.y() > home.y()) {
            preferred = Direction.NORTH;
        } else {
            preferred = randomDirection();
        }

        if (MovementUtils.isValidMove(game, preferred)) {
            return preferred;
        }
        return randomDirection();
    }

    private Direction randomDirection() {
        Direction[] values = Direction.values();
        return values[random.nextInt(values.length)];
    }
}
