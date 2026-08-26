package territorygame.controller;

import territorygame.api.AgentController;
import territorygame.api.CellViewType;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.VisibleCell;
import territorygame.helpers.MovementUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Framework code: the standard opponent used for assessment runs. Not part
 * of the candidate-facing surface.
 *
 * <p>Three states, organized by risk posture and picked fresh every turn
 * (highest priority first):
 * <ul>
 *   <li>{@code RECEDING} — safe. Our trail is long, we're low enough on
 *       turns that pushing further risks not making it back, the opponent
 *       is visible and close while we're exposed, or we're already ahead
 *       and the match is nearly over. Head for the nearest owned territory,
 *       or shuffle around inside it if we're already there.
 *   <li>{@code AGGRESSIVE} — risky. The opponent's trail or territory is
 *       visible and we're not currently in danger; go take it. Crossing
 *       their trail kills them; cutting a loop through their territory
 *       steals it on capture.
 *   <li>{@code WANDERING} — the default. Explore toward open space, but
 *       never deterministically: ties among comparably good directions are
 *       broken at random. On top of that, every turn independently has a
 *       small chance of forcing a few turns of {@code WANDERING} regardless
 *       of what else is going on.
 * </ul>
 *
 * <p>Earlier versions of this bot got stuck in short back-and-forth loops,
 * and a first fix (detecting an exact repeat in our own last few positions)
 * only covered loops of one particular length. The deeper problem is that
 * two instances of the same deterministic logic playing each other can
 * settle into a stable cycle that isn't a repeat of either agent's own
 * positions at all — each one's "best" move depends on the other's live
 * position. Rather than detect more and more cycle shapes, {@code
 * WANDERING} is simply never fully deterministic, and the random trigger
 * guarantees the whole match periodically gets nudged regardless of
 * whether a cycle would otherwise have formed.
 */
public final class EnemyStateMachine implements AgentController {

    /** Hard cap on trail length before heading home, independent of turns remaining. */
    private static final int MAX_TRAIL_BEFORE_RETURN = 8;
    /** Extra turns of margin required beyond the trail length itself before it's safe to keep pushing. */
    private static final int SAFETY_TURN_BUFFER = 4;
    /** Absolute "the match is nearly over" floor; getRemainingTurns() has no total to take a ratio of. */
    private static final int CONSOLIDATE_TURNS_THRESHOLD = 30;
    /** How far below the best open-space score a direction can be and still be considered "good enough" to wander into. */
    private static final int WANDER_OPENNESS_TOLERANCE = 1;
    /** Independent per-turn chance of forcing a few turns of WANDERING, regardless of what else is going on. */
    private static final double RANDOM_WANDER_CHANCE = 0.01;
    private static final int RANDOM_WANDER_DURATION = 2;

    private enum State {
        RECEDING, AGGRESSIVE, WANDERING
    }

    private final Random random = new Random();
    private int randomWanderTurnsRemaining;
    private State currentState = State.WANDERING;

    @Override
    public void takeTurn(GameApi game) {
        currentState = decideState(game);
        Direction direction = chooseDirection(game, currentState);
        game.move(direction);
    }

    @Override
    public String getDebugState() {
        return currentState.name();
    }

    // ---- State selection ----------------------------------------------

    private State decideState(GameApi game) {
        if (randomWanderTurnsRemaining > 0) {
            randomWanderTurnsRemaining--;
            return State.WANDERING;
        }
        if (random.nextDouble() < RANDOM_WANDER_CHANCE) {
            randomWanderTurnsRemaining = RANDOM_WANDER_DURATION - 1;
            return State.WANDERING;
        }
        if (shouldRecede(game)) {
            return State.RECEDING;
        }
        if (shouldBeAggressive(game)) {
            return State.AGGRESSIVE;
        }
        return State.WANDERING;
    }

    private boolean shouldRecede(GameApi game) {
        List<GridPosition> trail = game.getActiveTrail();
        if (!trail.isEmpty()) {
            if (trail.size() >= MAX_TRAIL_BEFORE_RETURN) {
                return true;
            }
            if (game.getRemainingTurns() <= trail.size() + SAFETY_TURN_BUFFER) {
                return true;
            }
            if (opponentIsThreateninglyClose(game)) {
                return true;
            }
        }
        return isEndgameWithLead(game);
    }

    private boolean shouldBeAggressive(GameApi game) {
        return nearestVisible(game, CellViewType.OPPONENT_TRAIL).isPresent()
                || nearestVisible(game, CellViewType.OPPONENT_TERRITORY).isPresent();
    }

    private boolean opponentIsThreateninglyClose(GameApi game) {
        int threatDistance = game.getVisibleGrid().length / 2;
        return nearestVisible(game, CellViewType.OPPONENT_AGENT)
                .map(position -> MovementUtils.manhattanDistance(game.getAgentPosition(), position) <= threatDistance)
                .orElse(false);
    }

    private boolean isEndgameWithLead(GameApi game) {
        return game.getRemainingTurns() <= CONSOLIDATE_TURNS_THRESHOLD
                && game.getOwnedTerritoryCellCount() > game.getOpponentTerritoryCellCount();
    }

    // ---- Direction selection --------------------------------------------

    private Direction chooseDirection(GameApi game, State state) {
        return switch (state) {
            case RECEDING -> pickReceding(game);
            case AGGRESSIVE -> pickAggressive(game);
            case WANDERING -> pickWandering(game);
        };
    }

    /** Stays inside our own territory if any safe move lands there (zero trail risk); otherwise heads for the nearest of it. */
    private Direction pickReceding(GameApi game) {
        List<Direction> withinTerritory = safeDirections(game).stream()
                .filter(direction -> typeAt(game, destination(game, direction)) == CellViewType.SELF_TERRITORY)
                .toList();
        if (!withinTerritory.isEmpty()) {
            return chooseBest(withinTerritory, Comparator.comparingInt((Direction d) -> openNeighborCount(game, d)).reversed());
        }
        GridPosition target = nearestVisible(game, CellViewType.SELF_TERRITORY).orElse(game.getRespawnPosition());
        return chooseBest(safeDirections(game), distanceTo(game, target));
    }

    /** Chases the opponent's trail for a kill if one's visible; otherwise cuts toward their territory to steal it on capture. */
    private Direction pickAggressive(GameApi game) {
        Optional<GridPosition> opponentTrail = nearestVisible(game, CellViewType.OPPONENT_TRAIL);
        if (opponentTrail.isPresent()) {
            return chooseBest(huntableDirections(game), distanceTo(game, opponentTrail.get()));
        }
        GridPosition target = nearestVisible(game, CellViewType.OPPONENT_TERRITORY).orElse(game.getAgentPosition());
        return chooseBest(safeDirections(game), distanceTo(game, target));
    }

    /** Picks at random among the directions whose open-space score is close to the best, so it's never fully predictable. */
    private Direction pickWandering(GameApi game) {
        List<Direction> candidates = safeDirections(game);
        if (candidates.isEmpty()) {
            return fallback();
        }
        int bestScore = candidates.stream().mapToInt(direction -> openNeighborCount(game, direction)).max().orElseThrow();
        List<Direction> goodEnough = candidates.stream()
                .filter(direction -> openNeighborCount(game, direction) >= bestScore - WANDER_OPENNESS_TOLERANCE)
                .toList();
        return goodEnough.get(random.nextInt(goodEnough.size()));
    }

    private Comparator<Direction> distanceTo(GameApi game, GridPosition target) {
        return Comparator.comparingInt(direction -> MovementUtils.manhattanDistance(destination(game, direction), target));
    }

    private Direction chooseBest(List<Direction> candidates, Comparator<Direction> ranking) {
        if (candidates.isEmpty()) {
            return fallback();
        }
        return candidates.stream().min(ranking).orElseThrow();
    }

    // ---- Board reading -----------------------------------------------------

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

    /** Like {@link #safeDirections}, but allows stepping onto the opponent's trail — that's the point of hunting. */
    private List<Direction> huntableDirections(GameApi game) {
        return List.of(Direction.values()).stream()
                .filter(direction -> MovementUtils.isValidBoardMove(
                        game.getAgentPosition(), direction, game.getBoardWidth(), game.getBoardHeight()))
                .filter(direction -> {
                    CellViewType type = typeAt(game, destination(game, direction));
                    return type != CellViewType.SELF_TRAIL && type != CellViewType.OPPONENT_AGENT;
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

    private Optional<GridPosition> nearestVisible(GameApi game, CellViewType type) {
        GridPosition from = game.getAgentPosition();
        GridPosition best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.type() == type) {
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
        return MovementUtils.findCell(game.getVisibleGrid(), position)
                .map(VisibleCell::type)
                .orElse(CellViewType.FREE);
    }

    private Direction fallback() {
        return MovementUtils.randomDirection(random);
    }
}
