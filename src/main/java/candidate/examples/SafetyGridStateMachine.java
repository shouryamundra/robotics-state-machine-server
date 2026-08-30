package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.helpers.MovementUtils;

import java.util.List;

/**
 * A cautious, methodical reference strategy: unlike {@code BasicStateMachine}
 * and {@code RandomStateMachine}, this one is meant to actually be studied.
 * It grows territory in small rectangular bites that always stay inside a
 * configurable box around its own head — so by construction it can never be
 * caught out in the open with no safe way home — disengages the instant the
 * opponent is sighted, and only fights when the opponent trespasses onto its
 * own land. See {@code docs/superpowers/specs/2026-08-29-safety-grid-state-machine-design.md}.
 */
public final class SafetyGridStateMachine implements AgentController {

    @Override
    public void takeTurn(GameApi game) {
        game.move(Direction.NORTH); // replaced in a later task
    }

    // ---- Pure helpers (no GameApi; unit-testable directly) ---------------

    static int chebyshevDistance(GridPosition a, GridPosition b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    /** Every cell in {@code trail} must stay within {@code gridSize / 2} of {@code head}. Vacuously true for an empty trail. */
    static boolean fitsSafetyGrid(GridPosition head, List<GridPosition> trail, int gridSize) {
        int half = gridSize / 2;
        for (GridPosition cell : trail) {
            if (chebyshevDistance(cell, head) > half) {
                return false;
            }
        }
        return true;
    }

    static Direction opposite(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }

    static List<Direction> perpendicularOptions(Direction direction) {
        return switch (direction) {
            case NORTH, SOUTH -> List.of(Direction.EAST, Direction.WEST);
            case EAST, WEST -> List.of(Direction.NORTH, Direction.SOUTH);
        };
    }

    /** Walks {@code steps} cells in the reverse of {@code outDirection} from {@code position}. */
    static GridPosition mirrorBack(GridPosition position, Direction outDirection, int steps) {
        GridPosition result = position;
        Direction reverse = opposite(outDirection);
        for (int i = 0; i < steps; i++) {
            result = MovementUtils.nextPosition(result, reverse);
        }
        return result;
    }

    static boolean isVertical(Direction direction) {
        return direction == Direction.NORTH || direction == Direction.SOUTH;
    }

    static boolean isOnSideOf(GridPosition cellPosition, GridPosition agentPosition, Direction direction) {
        return switch (direction) {
            case NORTH -> cellPosition.y() < agentPosition.y();
            case SOUTH -> cellPosition.y() > agentPosition.y();
            case EAST -> cellPosition.x() > agentPosition.x();
            case WEST -> cellPosition.x() < agentPosition.x();
        };
    }

    static boolean isWestOfMidline(GridPosition position, int boardWidth) {
        return position.x() < boardWidth / 2;
    }

    static boolean isOnHomeHalf(GridPosition currentPosition, GridPosition respawnPosition, int boardWidth) {
        return isWestOfMidline(currentPosition, boardWidth) == isWestOfMidline(respawnPosition, boardWidth);
    }

    static Direction enemyHalfDirection(GridPosition respawnPosition, int boardWidth) {
        return isWestOfMidline(respawnPosition, boardWidth) ? Direction.EAST : Direction.WEST;
    }
}
