package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.CellViewType;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.api.VisibleCell;
import territorygame.helpers.MovementUtils;

import java.util.List;
import java.util.Random;

/**
 * Deliberately weak reference implementation. It demonstrates persistent
 * controller state, state transitions, calling move(), and reacting to
 * MoveResult — not a strategy worth copying. EXPANDING still wanders
 * randomly and can walk over its own trail; RETURNING avoids its own trail
 * when it can, but only steps around it, not toward the nearest owned
 * territory. See README.md's Tips section for ideas (finding your nearest
 * territory, tracking the opponent, etc.) left undone here.
 */
public final class BasicStateMachine implements AgentController {

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

    @Override
    public String getDebugState() {
        return state.name();
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
        List<Direction> safeDirections = MovementUtils.validDirections(game);
        if (safeDirections.isEmpty()) {
            return MovementUtils.randomDirection(random);
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
            preferred = MovementUtils.randomDirection(random);
        }

        if (isSafeMove(game, preferred)) {
            return preferred;
        }

        // Preferred direction would cross our own trail: try any other
        // direction that avoids it before accepting that risk.
        for (Direction direction : Direction.values()) {
            if (isSafeMove(game, direction)) {
                return direction;
            }
        }

        // Nothing avoids our own trail — take the mechanically valid move anyway.
        if (MovementUtils.isValidMove(game, preferred)) {
            return preferred;
        }
        return MovementUtils.randomDirection(random);
    }

    /** Mechanically valid and not a step onto our own trail. */
    private boolean isSafeMove(GameApi game, Direction direction) {
        if (!MovementUtils.isValidMove(game, direction)) {
            return false;
        }
        GridPosition destination = MovementUtils.nextPosition(game.getAgentPosition(), direction);
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.position().equals(destination)) {
                    return cell.type() != CellViewType.SELF_TRAIL;
                }
            }
        }
        return true;
    }
}
