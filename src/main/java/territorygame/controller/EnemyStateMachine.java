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
 * <p>Five states, organized by risk posture and picked fresh every turn
 * (highest priority first):
 * <ul>
 *   <li>{@code DEFENSIVE} — our owned territory just shrank since last
 *       turn, meaning an opponent capture is in progress or just landed.
 *       Chase their visible trail for a kill if we can see one (the surest
 *       way to stop it cold); otherwise fall back to heading home.
 *   <li>{@code RECEDING} — safe. Our trail is long, we're low enough on
 *       turns that pushing further risks not making it back, the opponent
 *       is visible and close while we're exposed, or we're already ahead
 *       and the match is nearly over. Head for the nearest owned territory,
 *       or shuffle around inside it if we're already there.
 *   <li>{@code AGGRESSIVE} — risky. The opponent's trail or territory is
 *       visible and we're not currently in danger; go take it. Crossing
 *       their trail kills them; cutting a loop through their territory
 *       steals it on capture.
 *   <li>{@code EXPANDING} — the default. Deterministically push toward
 *       whichever safe direction opens onto the most free space.
 *   <li>{@code WANDERING} — a rare, single-turn detour: pick at random
 *       among directions whose open-space score is merely close to the
 *       best, instead of the single best one. Never fires two turns in a
 *       row, so it's a brief nudge, not a resting state.
 * </ul>
 *
 * <p>Earlier versions of this bot got stuck in short back-and-forth loops.
 * The deeper problem is that two instances of the same deterministic logic
 * playing each other can settle into a stable cycle that isn't a repeat of
 * either agent's own positions at all — each one's "best" move depends on
 * the other's live position. Rather than detect specific cycle shapes,
 * {@code WANDERING} periodically (and briefly) makes the whole match
 * non-deterministic, which is enough to knock either agent off any cycle
 * regardless of its length.
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
    /** Per-turn chance of a single WANDERING detour, skipped entirely when we just wandered last turn. */
    private static final double RANDOM_WANDER_CHANCE = 0.01;

    private enum State {
        DEFENSIVE, RECEDING, AGGRESSIVE, EXPANDING, WANDERING
    }

    private final Random random;
    private State currentState = State.EXPANDING;
    private int previousOwnedTerritoryCount;

    public EnemyStateMachine() {
        this(new Random().nextLong());
    }

    /** Two instances of this same deterministic logic need different seeds, or they'll play out identically for long stretches. */
    public EnemyStateMachine(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public void takeTurn(GameApi game) {
        State previousState = currentState;
        currentState = decideState(game, previousState);
        Direction direction = chooseDirection(game, currentState);
        game.move(direction);
    }

    @Override
    public String getDebugState() {
        return currentState.name();
    }

    // ---- State selection ----------------------------------------------

    private State decideState(GameApi game, State previousState) {
        boolean territoryShrank = game.getOwnedTerritoryCellCount() < previousOwnedTerritoryCount;
        previousOwnedTerritoryCount = game.getOwnedTerritoryCellCount();
        if (territoryShrank) {
            return State.DEFENSIVE;
        }
        if (shouldRecede(game)) {
            return State.RECEDING;
        }
        if (shouldBeAggressive(game)) {
            return State.AGGRESSIVE;
        }
        if (previousState != State.WANDERING && random.nextDouble() < RANDOM_WANDER_CHANCE) {
            return State.WANDERING;
        }
        return State.EXPANDING;
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
            case DEFENSIVE -> pickDefensive(game);
            case RECEDING -> pickReceding(game);
            case AGGRESSIVE -> pickAggressive(game);
            case EXPANDING -> pickExpanding(game);
            case WANDERING -> pickWandering(game);
        };
    }

    /** Chases the opponent's visible trail to stop an in-progress capture cold; otherwise falls back to heading home. */
    private Direction pickDefensive(GameApi game) {
        Optional<GridPosition> opponentTrail = nearestVisible(game, CellViewType.OPPONENT_TRAIL);
        if (opponentTrail.isPresent()) {
            return chooseBest(huntableDirections(game), distanceTo(game, opponentTrail.get()));
        }
        return pickReceding(game);
    }

    /** Deterministically pushes toward whichever safe direction opens onto the most free space. */
    private Direction pickExpanding(GameApi game) {
        return chooseBest(safeDirections(game), Comparator.comparingInt((Direction d) -> openNeighborCount(game, d)).reversed());
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
