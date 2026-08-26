package territorygame.controller;

import territorygame.api.AgentController;
import territorygame.api.CellViewType;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.api.VisibleCell;
import territorygame.helpers.MovementUtils;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Framework code: the standard opponent used for assessment runs.
 * Deterministic and simple, but harder to trip up than the candidate-facing
 * example controllers — it hard-avoids both trails (not just its own),
 * returns home after short excursions, and prefers open space when
 * expanding. Not part of the candidate-facing surface.
 */
public final class ProvidedBotController implements AgentController {

    private static final int RETURN_TRAIL_THRESHOLD = 3;

    private enum State {
        EXPANDING,
        RETURNING
    }

    private State state = State.EXPANDING;
    private final Random random = new Random(99);

    @Override
    public void takeTurn(GameApi game) {
        Direction direction = state == State.EXPANDING
                ? chooseExpandingDirection(game)
                : chooseReturningDirection(game);

        MoveResult result = game.move(direction);

        if (result == MoveResult.CAPTURED || result == MoveResult.DIED) {
            state = State.EXPANDING;
        } else if (state == State.EXPANDING && game.getActiveTrail().size() >= RETURN_TRAIL_THRESHOLD) {
            state = State.RETURNING;
        } else if (state == State.RETURNING && game.getActiveTrail().isEmpty()) {
            state = State.EXPANDING;
        }
    }

    private Direction chooseExpandingDirection(GameApi game) {
        List<Direction> safe = safeDirections(game);
        if (safe.isEmpty()) {
            return fallback();
        }
        return safe.stream()
                .max((a, b) -> Integer.compare(openNeighborCount(game, a), openNeighborCount(game, b)))
                .orElseThrow();
    }

    private Direction chooseReturningDirection(GameApi game) {
        List<Direction> safe = safeDirections(game);
        if (safe.isEmpty()) {
            return fallback();
        }
        Optional<GridPosition> target = nearestSelfTerritory(game);
        if (target.isEmpty()) {
            return safe.get(random.nextInt(safe.size()));
        }
        return safe.stream()
                .min((a, b) -> Integer.compare(
                        MovementUtils.manhattanDistance(destination(game, a), target.get()),
                        MovementUtils.manhattanDistance(destination(game, b), target.get())))
                .orElseThrow();
    }

    /** Directions that are in bounds and land on neither trail nor the opponent's agent. */
    private List<Direction> safeDirections(GameApi game) {
        return List.of(Direction.values()).stream()
                .filter(direction -> MovementUtils.isValidBoardMove(
                        game.getAgentPosition(), direction, game.getBoardWidth(), game.getBoardHeight()))
                .filter(direction -> {
                    CellViewType type = typeAt(game, destination(game, direction));
                    return type != CellViewType.SELF_TRAIL
                            && type != CellViewType.OPPONENT_TRAIL
                            && type != CellViewType.OPPONENT_AGENT;
                })
                .toList();
    }

    /** Count of FREE cells cardinally adjacent to the given direction's destination, a cheap open-space heuristic. */
    private int openNeighborCount(GameApi game, Direction direction) {
        GridPosition destination = destination(game, direction);
        int count = 0;
        for (Direction neighborDirection : Direction.values()) {
            GridPosition neighbor = MovementUtils.nextPosition(destination, neighborDirection);
            if (typeAt(game, neighbor) == CellViewType.FREE) {
                count++;
            }
        }
        return count;
    }

    private Optional<GridPosition> nearestSelfTerritory(GameApi game) {
        GridPosition from = game.getAgentPosition();
        GridPosition best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.type() == CellViewType.SELF_TERRITORY) {
                    int distance = MovementUtils.manhattanDistance(from, cell.position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cell.position();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private GridPosition destination(GameApi game, Direction direction) {
        return MovementUtils.nextPosition(game.getAgentPosition(), direction);
    }

    private CellViewType typeAt(GameApi game, GridPosition position) {
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.position().equals(position)) {
                    return cell.type();
                }
            }
        }
        return CellViewType.FREE;
    }

    private Direction fallback() {
        Direction[] values = Direction.values();
        return values[random.nextInt(values.length)];
    }
}
