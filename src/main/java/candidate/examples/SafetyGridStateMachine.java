package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.OccupantView;
import territorygame.api.TerritoryView;
import territorygame.api.VisibleCell;
import territorygame.helpers.MovementUtils;
import territorygame.helpers.ObservedBoard;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    private static final int AWAY_GRID_SIZE = 5;
    private static final int HOME_GRID_SIZE = 9;
    private static final double TERRITORY_VISIBLE_ENTER_THRESHOLD = 0.90;
    private static final double TERRITORY_VISIBLE_EXIT_THRESHOLD = 0.75;
    private static final double VERTICAL_WEIGHT = 1.5;
    /** Keeps treating the opponent as a threat for this many turns after we last actually saw them, so a
     *  single frame of them stepping out of our window doesn't cause an immediate dart-back-out. */
    private static final int AVOID_COOLDOWN_TURNS = 3;

    private enum Phase {
        ATTACK, AVOID, REPOSITION, OUT, ACROSS, BACK
    }

    private Phase phase = Phase.OUT;
    private Direction outDirection;
    private int stepsOut;
    private Direction acrossDirection;
    private ObservedBoard observedBoard;
    private GridPosition lastKnownOpponentPosition;
    private int turnsSinceOpponentSeen = Integer.MAX_VALUE;
    private Direction lastDirection;
    private GridPosition repositionTarget;
    private boolean repositioning;

    @Override
    public void takeTurn(GameApi game) {
        if (observedBoard == null) {
            observedBoard = new ObservedBoard(game.getBoardWidth(), game.getBoardHeight());
        }
        observedBoard.update(game.getVisibleGrid());

        Optional<GridPosition> opponentNow = findOpponentPosition(game);
        if (opponentNow.isPresent()) {
            lastKnownOpponentPosition = opponentNow.get();
            turnsSinceOpponentSeen = 0;
        } else if (turnsSinceOpponentSeen < Integer.MAX_VALUE) {
            turnsSinceOpponentSeen++;
        }
        boolean opponentThreat = turnsSinceOpponentSeen < AVOID_COOLDOWN_TURNS;

        Optional<GridPosition> intrusion = findOpponentTrailOnOurTerritory(game);
        Direction direction;
        if (intrusion.isPresent()) {
            phase = Phase.ATTACK;
            direction = pickAttack(game, intrusion.get());
        } else if (opponentThreat) {
            phase = Phase.AVOID;
            direction = pickAvoid(game);
        } else if (game.getActiveTrail().isEmpty()) {
            // Hysteresis, same idea as AVOID_COOLDOWN_TURNS above: entering REPOSITION needs a strong
            // 90%-visible-territory signal, but once in it we only leave once that drops further, below
            // 75%. Without a band here, standing near a board edge (where the visible window itself
            // shrinks/grows by a column as we take one step) can flip the raw fraction across a single
            // threshold every turn and thrash between REPOSITION and a fresh expedition forever.
            double territoryFraction = visibleTerritoryFraction(game);
            double activeThreshold = repositioning ? TERRITORY_VISIBLE_EXIT_THRESHOLD : TERRITORY_VISIBLE_ENTER_THRESHOLD;
            repositioning = territoryFraction >= activeThreshold;
            if (repositioning) {
                phase = Phase.REPOSITION;
                direction = pickReposition(game);
            } else {
                direction = pickExpedition(game);
            }
        } else {
            direction = pickExpedition(game);
        }
        lastDirection = direction;
        game.move(direction);
    }

    @Override
    public String getDebugState() {
        return phase.name()
                + "\noutDirection: " + outDirection
                + "\nstepsOut: " + stepsOut
                + "\nacrossDirection: " + acrossDirection
                + "\nturnsSinceOpponentSeen: " + turnsSinceOpponentSeen
                + "\nrepositionTarget: " + repositionTarget;
    }

    // ---- ATTACK / AVOID / REPOSITION ------------------------------------

    /** A free kill: crossing their trail sends them back to respawn, no risk to us. */
    private Direction pickAttack(GameApi game, GridPosition target) {
        return chooseBest(game, huntableDirections(game), distanceTo(game, target));
    }

    /** Retreats if mid-expedition; if already home, kites away from the opponent while staying on our own territory. */
    private Direction pickAvoid(GameApi game) {
        if (!game.getActiveTrail().isEmpty()) {
            return pickBack(game);
        }
        // Use the last-known sighting, not a fresh lookup: during the cooldown window the opponent may have
        // stepped out of view this exact turn, and kiting off a stale-but-recent position beats panicking.
        GridPosition opponentPosition = lastKnownOpponentPosition;
        List<Direction> territoryOnly = safeDirections(game).stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (territoryOnly.isEmpty()) {
            return pickBack(game);
        }
        Comparator<Direction> farthestFirst = Comparator.comparingInt(
                (Direction direction) -> -MovementUtils.manhattanDistance(destination(game, direction), opponentPosition));
        // preferContinuing is the *last* tie-break, not the first: it only fires once distance and openness
        // are both exactly tied, so it can never override an actually-better escape route.
        return chooseBest(game, territoryOnly,
                farthestFirst.thenComparing(mostOpenFirst(game)).thenComparing(preferContinuing(lastDirection)));
    }

    /**
     * Walks toward the most-advanced edge of our own territory (see {@link #pickRepositionTarget}),
     * preferring moves that stay on our own territory so no trail risk is taken. Unlike scoring raw local
     * openness fresh every turn, this target is a fixed point in the world rather than something derived
     * from our own current position — so it doesn't flip back and forth as our position shifts by a cell.
     */
    private Direction pickReposition(GameApi game) {
        repositionTarget = pickRepositionTarget(game);
        if (repositionTarget.equals(game.getAgentPosition())) {
            // We're already standing on the most-advanced territory we can see — there's nowhere better
            // to pace toward, so stop "repositioning" in place and start a fresh expedition from right here.
            return pickExpedition(game);
        }
        List<Direction> candidates = safeDirections(game);
        List<Direction> territoryOnly = candidates.stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (!territoryOnly.isEmpty()) {
            candidates = territoryOnly;
        }
        // Once we've arrived (or every candidate ties on distance), stick with whatever direction we
        // used last turn instead of picking arbitrarily among options that are all equally close.
        return chooseBest(game, candidates, distanceTo(game, repositionTarget).thenComparing(preferContinuing(lastDirection)));
    }

    /**
     * A launch point for the next expedition: the most-advanced-toward-the-enemy cell of our own
     * territory that itself borders unclaimed/opponent land, so a fresh OUT expedition can leave
     * territory immediately once we arrive (no disagreement with {@link #pickOutDirection}'s own
     * dead-end handling about which way is actually useful). Falls back to the single most-advanced
     * reachable cell if no visible border tile qualifies (e.g. we can't yet see past our own territory).
     * <p>
     * The target must be a self-territory cell reachable via other territory cells alone — not just any
     * empty/opponent cell, and not an island of land disconnected from where we're standing — because
     * movement here is deliberately restricted to territory-only moves (no trail risk); picking a target
     * we can't legally walk to would mean approaching its border forever without ever reaching it.
     */
    private GridPosition pickRepositionTarget(GameApi game) {
        Direction towardEnemy = enemyHalfDirection(game.getRespawnPosition(), game.getBoardWidth());
        GridPosition from = game.getAgentPosition();
        Set<GridPosition> visibleSelfTerritory = new HashSet<>();
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.territory() == TerritoryView.SELF) {
                    visibleSelfTerritory.add(cell.position());
                }
            }
        }
        Set<GridPosition> reachable = floodFillReachable(from, visibleSelfTerritory);

        GridPosition bestBorder = null;
        int bestBorderAdvancement = Integer.MIN_VALUE;
        GridPosition bestAny = from;
        int bestAnyAdvancement = advancementToward(from, towardEnemy);
        for (GridPosition position : reachable) {
            int advancement = advancementToward(position, towardEnemy);
            if (advancement > bestAnyAdvancement) {
                bestAnyAdvancement = advancement;
                bestAny = position;
            }
            if (advancement > bestBorderAdvancement && bordersNonSelfTerritory(game, position)) {
                bestBorderAdvancement = advancement;
                bestBorder = position;
            }
        }
        return bestBorder != null ? bestBorder : bestAny;
    }

    private boolean bordersNonSelfTerritory(GameApi game, GridPosition position) {
        for (Direction direction : Direction.values()) {
            GridPosition neighbor = MovementUtils.nextPosition(position, direction);
            boolean nonSelf = MovementUtils.findCell(game.getVisibleGrid(), neighbor)
                    .map(cell -> cell.territory() != TerritoryView.SELF)
                    .orElse(false);
            if (nonSelf) {
                return true;
            }
        }
        return false;
    }

    /** Every cell in {@code allowed} reachable from {@code start} by taking single steps through {@code allowed}. */
    private static Set<GridPosition> floodFillReachable(GridPosition start, Set<GridPosition> allowed) {
        Set<GridPosition> reachable = new HashSet<>();
        Deque<GridPosition> frontier = new ArrayDeque<>();
        reachable.add(start);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            GridPosition current = frontier.poll();
            for (Direction direction : Direction.values()) {
                GridPosition neighbor = MovementUtils.nextPosition(current, direction);
                if (allowed.contains(neighbor) && reachable.add(neighbor)) {
                    frontier.add(neighbor);
                }
            }
        }
        return reachable;
    }

    /** How far a position is toward {@code direction}, as a single comparable number — larger is farther. */
    private static int advancementToward(GridPosition position, Direction direction) {
        return switch (direction) {
            case EAST -> position.x();
            case WEST -> -position.x();
            case SOUTH -> position.y();
            case NORTH -> -position.y();
        };
    }

    // ---- Expedition: OUT / ACROSS / BACK --------------------------------

    private int gridSizeFor(GameApi game) {
        boolean home = isOnHomeHalf(game.getAgentPosition(), game.getRespawnPosition(), game.getBoardWidth());
        return home ? HOME_GRID_SIZE : AWAY_GRID_SIZE;
    }

    /** Starts a fresh expedition whenever we're standing on territory; otherwise continues whichever phase we're in. */
    private Direction pickExpedition(GameApi game) {
        if (game.getActiveTrail().isEmpty()) {
            phase = Phase.OUT;
            outDirection = pickOutDirection(game);
            stepsOut = 0;
        }
        return switch (phase) {
            case OUT -> pickOut(game);
            case ACROSS -> pickAcross(game);
            // ATTACK/AVOID/REPOSITION never reach here; BACK, and any interrupted-then-resumed
            // phase left over from an ATTACK/AVOID detour, both just keep heading home.
            default -> pickBack(game);
        };
    }

    /**
     * Picks which way to head out. Only considers directions that actually leave our own territory —
     * otherwise, deep inside a big blob, we'd "expedition" in place forever: laying no trail, capturing
     * nothing, and just walking back and forth as the local open-cell count flips by one cell each step.
     * In the rare case every adjacent cell is already ours, head toward the nearest visible cell that
     * isn't instead of picking blind.
     */
    private Direction pickOutDirection(GameApi game) {
        List<Direction> leavingTerritory = safeDirections(game).stream()
                .filter(direction -> !isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (!leavingTerritory.isEmpty()) {
            // Rank by openness first, same as the spec; only fall back to the direction we bit into last
            // time once openness is an exact tie, so consecutive expeditions widen the same edge into a
            // rectangle instead of alternating — without ever overriding an actually-more-open direction.
            return chooseBest(game, leavingTerritory, mostOpenFirst(game).thenComparing(preferContinuing(outDirection)));
        }
        return nearestKnownNonSelfTerritory(game)
                .map(frontier -> chooseBest(game, safeDirections(game), distanceTo(game, frontier)))
                .orElseGet(() -> chooseBest(game, safeDirections(game), mostOpenFirst(game)));
    }

    private Direction pickOut(GameApi game) {
        GridPosition nextHead = destination(game, outDirection);
        if (safeDirections(game).contains(outDirection) && fitsSafetyGrid(nextHead, game.getActiveTrail(), gridSizeFor(game))) {
            stepsOut++;
            return outDirection;
        }
        phase = Phase.ACROSS;
        acrossDirection = pickAcrossDirection(game);
        return pickAcross(game);
    }

    private Direction pickAcrossDirection(GameApi game) {
        List<Direction> safe = safeDirections(game);
        List<Direction> perpendicular = perpendicularOptions(outDirection).stream()
                .filter(safe::contains)
                .toList();
        if (perpendicular.isEmpty()) {
            return outDirection; // both perpendicular options are blocked; this will fail the grid/mirror check below and fall back to BACK
        }
        return chooseBest(game, perpendicular, mostOpenFirst(game));
    }

    private Direction pickAcross(GameApi game) {
        if (canTakeAnotherAcrossStep(game)) {
            return acrossDirection;
        }
        phase = Phase.BACK;
        return pickBack(game);
    }

    private boolean canTakeAnotherAcrossStep(GameApi game) {
        if (!safeDirections(game).contains(acrossDirection)) {
            return false;
        }
        GridPosition nextHead = destination(game, acrossDirection);
        if (!fitsSafetyGrid(nextHead, game.getActiveTrail(), gridSizeFor(game))) {
            return false;
        }
        GridPosition mirrored = mirrorBack(nextHead, outDirection, stepsOut);
        return isKnownSelfTerritory(game, mirrored);
    }

    /** Like {@link #isSelfTerritory}, but falls back to {@code observedBoard} for cells outside the live window. */
    private boolean isKnownSelfTerritory(GameApi game, GridPosition position) {
        if (!MovementUtils.isWithinBoard(position, game.getBoardWidth(), game.getBoardHeight())) {
            return false;
        }
        Optional<VisibleCell> live = MovementUtils.findCell(game.getVisibleGrid(), position);
        if (live.isPresent()) {
            return live.get().territory() == TerritoryView.SELF;
        }
        return observedBoard.get(position)
                .map(cell -> cell.territory() == TerritoryView.SELF)
                .orElse(false);
    }

    // ---- Shared BACK logic ------------------------------------------------

    /** Stays inside our own territory if any safe move lands there; otherwise heads for the nearest known owned cell. */
    private Direction pickBack(GameApi game) {
        List<Direction> candidates = safeDirections(game);
        List<Direction> withinTerritory = candidates.stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (!withinTerritory.isEmpty()) {
            return chooseBest(game, withinTerritory, mostOpenFirst(game));
        }
        GridPosition target = nearestKnownSelfTerritory(game).orElse(game.getRespawnPosition());
        return chooseBest(game, candidates, distanceTo(game, target));
    }

    // ---- Board reading -----------------------------------------------------

    private List<Direction> safeDirections(GameApi game) {
        return List.of(Direction.values()).stream()
                .filter(direction -> MovementUtils.isValidBoardMove(
                        game.getAgentPosition(), direction, game.getBoardWidth(), game.getBoardHeight()))
                .filter(direction -> {
                    OccupantView occupant = occupantAt(game, destination(game, direction));
                    return occupant != OccupantView.SELF_TRAIL
                            && occupant != OccupantView.OPPONENT_TRAIL
                            && occupant != OccupantView.OPPONENT_AGENT;
                })
                .toList();
    }

    /** Like {@link #safeDirections}, but allows stepping onto the opponent's trail — that's how a chase ends in a kill. */
    private List<Direction> huntableDirections(GameApi game) {
        return List.of(Direction.values()).stream()
                .filter(direction -> MovementUtils.isValidBoardMove(
                        game.getAgentPosition(), direction, game.getBoardWidth(), game.getBoardHeight()))
                .filter(direction -> {
                    OccupantView occupant = occupantAt(game, destination(game, direction));
                    return occupant != OccupantView.SELF_TRAIL && occupant != OccupantView.OPPONENT_AGENT;
                })
                .toList();
    }

    private OccupantView occupantAt(GameApi game, GridPosition position) {
        return MovementUtils.findCell(game.getVisibleGrid(), position)
                .map(VisibleCell::occupant)
                .orElse(OccupantView.EMPTY);
    }

    private GridPosition destination(GameApi game, Direction direction) {
        return MovementUtils.nextPosition(game.getAgentPosition(), direction);
    }

    /** {@code false} for cells outside the visible window — use {@code isKnownSelfTerritory} once that exists (Task 4). */
    private boolean isSelfTerritory(GameApi game, GridPosition position) {
        return MovementUtils.findCell(game.getVisibleGrid(), position)
                .map(cell -> cell.territory() == TerritoryView.SELF)
                .orElse(false);
    }

    private Direction chooseBest(GameApi game, List<Direction> candidates, Comparator<Direction> ranking) {
        if (candidates.isEmpty()) {
            return fallback(game);
        }
        return candidates.stream().min(ranking).orElseThrow();
    }

    private Comparator<Direction> distanceTo(GameApi game, GridPosition target) {
        return Comparator.comparingInt(direction -> MovementUtils.manhattanDistance(destination(game, direction), target));
    }

    /** Counts visible unclaimed cells on each side of the agent; NORTH/SOUTH counts are weighted higher. */
    private int opennessScore(GameApi game, Direction direction) {
        GridPosition agentPosition = game.getAgentPosition();
        int count = 0;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.territory() == TerritoryView.UNOWNED && isOnSideOf(cell.position(), agentPosition, direction)) {
                    count++;
                }
            }
        }
        return isVertical(direction) ? (int) (count * VERTICAL_WEIGHT) : count;
    }

    private Comparator<Direction> mostOpenFirst(GameApi game) {
        return Comparator.comparingInt((Direction direction) -> opennessScore(game, direction)).reversed();
    }

    private Optional<GridPosition> nearestKnownSelfTerritory(GameApi game) {
        GridPosition from = game.getAgentPosition();
        GridPosition best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.territory() == TerritoryView.SELF) {
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

    /**
     * Nearest cell that isn't ours — the closest exit out of our own territory, in any direction. Searches
     * everything we've ever observed, not just the currently-visible window: deep inside a large blob, the
     * live window can be entirely self-territory even though we've walked past its edge before.
     */
    private Optional<GridPosition> nearestKnownNonSelfTerritory(GameApi game) {
        GridPosition from = game.getAgentPosition();
        GridPosition best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = 0; y < game.getBoardHeight(); y++) {
            for (int x = 0; x < game.getBoardWidth(); x++) {
                GridPosition position = new GridPosition(x, y);
                boolean nonSelf = observedBoard.get(position)
                        .map(cell -> cell.territory() != TerritoryView.SELF)
                        .orElse(false);
                if (nonSelf) {
                    int distance = MovementUtils.manhattanDistance(from, position);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = position;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<GridPosition> findOpponentPosition(GameApi game) {
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.occupant() == OccupantView.OPPONENT_AGENT) {
                    return Optional.of(cell.position());
                }
            }
        }
        return Optional.empty();
    }

    /** Nearest visible cell where the opponent's trail is crossing land that's ours — an intrusion worth punishing. */
    private Optional<GridPosition> findOpponentTrailOnOurTerritory(GameApi game) {
        GridPosition from = game.getAgentPosition();
        GridPosition best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.territory() == TerritoryView.SELF && cell.occupant() == OccupantView.OPPONENT_TRAIL) {
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

    private double visibleTerritoryFraction(GameApi game) {
        int total = 0;
        int selfTerritory = 0;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                total++;
                if (cell.territory() == TerritoryView.SELF) {
                    selfTerritory++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) selfTerritory / total;
    }

    private Direction fallback(GameApi game) {
        List<Direction> valid = MovementUtils.validDirections(game);
        return valid.isEmpty() ? Direction.NORTH : valid.get(0);
    }

    // ---- Pure helpers (no GameApi; unit-testable directly) ---------------

    /**
     * Tie-break helper: ranks {@code previous} ahead of every other direction, and all others as equal to
     * each other. Meant to be chained in front of a noisy/near-tied comparator (like {@code mostOpenFirst})
     * so that marginal score differences don't cause flip-flopping between two similarly-good directions
     * from one turn to the next.
     */
    static Comparator<Direction> preferContinuing(Direction previous) {
        return Comparator.comparingInt(direction -> direction == previous ? 0 : 1);
    }

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
