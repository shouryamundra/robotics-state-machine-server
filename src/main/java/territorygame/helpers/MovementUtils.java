package territorygame.helpers;

import territorygame.api.CellViewType;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.VisibleCell;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Pure, stateless helpers for reasoning about movement and board bounds. */
public final class MovementUtils {

    private MovementUtils() {
    }

    public static GridPosition nextPosition(GridPosition position, Direction direction) {
        return switch (direction) {
            case NORTH -> new GridPosition(position.x(), position.y() - 1);
            case SOUTH -> new GridPosition(position.x(), position.y() + 1);
            case EAST -> new GridPosition(position.x() + 1, position.y());
            case WEST -> new GridPosition(position.x() - 1, position.y());
        };
    }

    public static boolean isWithinBoard(GridPosition position, int width, int height) {
        return position.x() >= 0 && position.x() < width
                && position.y() >= 0 && position.y() < height;
    }

    public static boolean isValidBoardMove(GridPosition position, Direction direction, int width, int height) {
        return isWithinBoard(nextPosition(position, direction), width, height);
    }

    /** Grid (non-diagonal) distance between two positions. */
    public static int manhattanDistance(GridPosition a, GridPosition b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }

    /**
     * Checks the two mechanical invalid-move rules available before
     * submission: board bounds and whether the adjacent destination is
     * currently the opponent's agent. Makes no strategic decision and picks
     * no alternative direction.
     */
    public static boolean isValidMove(GameApi game, Direction direction) {
        GridPosition destination = nextPosition(game.getAgentPosition(), direction);
        if (!isWithinBoard(destination, game.getBoardWidth(), game.getBoardHeight())) {
            return false;
        }
        // Visibility radius always covers adjacent cells, so an absent cell
        // here is unreachable in practice; bounds are already confirmed above.
        return findCell(game.getVisibleGrid(), destination)
                .map(cell -> cell.type() != CellViewType.OPPONENT_AGENT)
                .orElse(true);
    }

    /** Finds the visible cell at an absolute position, if it's within the visible window. */
    public static Optional<VisibleCell> findCell(VisibleCell[][] visibleGrid, GridPosition position) {
        for (VisibleCell[] row : visibleGrid) {
            for (VisibleCell cell : row) {
                if (cell.position().equals(position)) {
                    return Optional.of(cell);
                }
            }
        }
        return Optional.empty();
    }

    /** Directions that are mechanically valid right now: in bounds and not onto the opponent's agent. */
    public static List<Direction> validDirections(GameApi game) {
        List<Direction> directions = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (isValidMove(game, direction)) {
                directions.add(direction);
            }
        }
        return directions;
    }

    /** Picks a uniformly random direction. */
    public static Direction randomDirection(Random random) {
        Direction[] values = Direction.values();
        return values[random.nextInt(values.length)];
    }
}
