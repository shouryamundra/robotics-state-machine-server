package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.OccupantView;
import territorygame.api.TerritoryView;
import territorygame.api.VisibleCell;
import territorygame.helpers.MovementUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A cautious, methodical reference strategy: unlike {@code BasicStateMachine}
 * and {@code RandomStateMachine}, this one is meant to actually be studied.
 * It grows territory in small rectangular bites that always stay inside a
 * configurable box around its own head — so by construction it can never be
 * caught out in the open with no safe way home — disengages the instant the
 * opponent is sighted, and only fights when the opponent trespasses onto its
 * own land. See {@code docs/superpowers/specs/2026-08-29-safety-grid-state-machine-design.md}
 * and {@code docs/superpowers/specs/2026-08-30-safety-grid-edge-expeditions-design.md}.
 */
public final class SafetyGridStateMachine implements AgentController {

    private static final int SAFETY_GRID_SIZE = 5;
    private static final int OUT_LOOKAHEAD = 3;

    private enum Phase {
        ATTACK, AVOID, REPOSITION, OUT, ACROSS, BACK
    }

    private Phase phase = Phase.OUT;
    private Direction outDirection;
    private int stepsOut;
    private Direction acrossDirection;
    private Direction repositionDirection;
    private final Random random = new Random(400);

    @Override
    public void takeTurn(GameApi game) {
        Optional<GridPosition> intrusion = findOpponentTrailOnOurTerritory(game);
        Direction direction;
        if (intrusion.isPresent()) {
            phase = Phase.ATTACK;
            direction = pickAttack(game, intrusion.get());
        } else if (isOpponentVisible(game)) {
            phase = Phase.AVOID;
            direction = pickAvoid(game);
        } else if (game.getActiveTrail().isEmpty()) {
            direction = pickTrailFree(game);
        } else {
            direction = pickExpedition(game);
        }
        game.move(direction);
    }

    @Override
    public String getDebugState() {
        return phase.name();
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
        GridPosition opponentPosition = findOpponentPosition(game).orElseThrow();
        List<Direction> territoryOnly = safeDirections(game).stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (territoryOnly.isEmpty()) {
            return pickBack(game);
        }
        Comparator<Direction> farthestFirst = Comparator.comparingInt(
                (Direction direction) -> -MovementUtils.manhattanDistance(destination(game, direction), opponentPosition));
        return chooseBest(game, territoryOnly, farthestFirst);
    }

    /** Chooses a move on owned territory or starts OUT when the chosen move leaves it. */
    private Direction pickTrailFree(GameApi game) {
        List<Direction> safe = safeDirections(game);
        List<Direction> territoryOnly = safe.stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();

        if (repositionDirection != null
                && territoryOnly.contains(repositionDirection)
                && distanceToOutsideTerritory(game, repositionDirection) <= OUT_LOOKAHEAD) {
            phase = Phase.REPOSITION;
            return repositionDirection;
        }

        List<Direction> outsideTerritory = safe.stream()
                .filter(direction -> !territoryOnly.contains(direction))
                .toList();
        if (!outsideTerritory.isEmpty()) {
            phase = Phase.OUT;
            outDirection = outsideTerritory.contains(repositionDirection)
                    ? repositionDirection
                    : chooseRandom(game, preferVertical(outsideTerritory));
            repositionDirection = null;
            stepsOut = 1;
            return outDirection;
        }

        phase = Phase.REPOSITION;
        List<Direction> approaches = closestApproachDirections(game, territoryOnly);
        if (!approaches.isEmpty()) {
            repositionDirection = chooseRandom(game, approaches);
            return repositionDirection;
        }
        if (repositionDirection != null && territoryOnly.contains(repositionDirection)) {
            return repositionDirection;
        }
        repositionDirection = chooseRandom(game, territoryOnly.isEmpty() ? safe : territoryOnly);
        return repositionDirection;
    }

    // ---- Expedition: OUT / ACROSS / BACK --------------------------------

    /** Continues whichever part of an active expedition was last selected. */
    private Direction pickExpedition(GameApi game) {
        return switch (phase) {
            case OUT -> pickOut(game);
            case ACROSS -> pickAcross(game);
            // ATTACK/AVOID/REPOSITION never reach here; BACK, and any interrupted-then-resumed
            // phase left over from an ATTACK/AVOID detour, both just keep heading home.
            default -> pickBack(game);
        };
    }

    private Direction pickOut(GameApi game) {
        GridPosition nextHead = destination(game, outDirection);
        if (safeDirections(game).contains(outDirection)
                && !isSelfTerritory(game, nextHead)
                && fitsSafetyGrid(nextHead, game.getActiveTrail(), SAFETY_GRID_SIZE)) {
            stepsOut++;
            return outDirection;
        }
        phase = Phase.ACROSS;
        acrossDirection = pickAcrossDirection(game);
        return pickAcross(game);
    }

    private Direction pickAcrossDirection(GameApi game) {
        List<Direction> perpendicular = perpendicularOptions(outDirection).stream()
                .filter(direction -> canTakeAnotherAcrossStep(game, direction))
                .toList();
        if (perpendicular.isEmpty()) {
            return outDirection; // both perpendicular options are blocked; this will fail the grid/mirror check below and fall back to BACK
        }
        return chooseRandom(game, perpendicular);
    }

    private Direction pickAcross(GameApi game) {
        if (canTakeAnotherAcrossStep(game)) {
            return acrossDirection;
        }
        phase = Phase.BACK;
        return pickBack(game);
    }

    private boolean canTakeAnotherAcrossStep(GameApi game) {
        return canTakeAnotherAcrossStep(game, acrossDirection);
    }

    private boolean canTakeAnotherAcrossStep(GameApi game, Direction direction) {
        if (!safeDirections(game).contains(direction)) {
            return false;
        }
        GridPosition nextHead = destination(game, direction);
        if (!fitsSafetyGrid(nextHead, game.getActiveTrail(), SAFETY_GRID_SIZE)) {
            return false;
        }
        GridPosition mirrored = mirrorBack(nextHead, outDirection, stepsOut);
        return isSelfTerritory(game, mirrored);
    }

    // ---- Shared BACK logic ------------------------------------------------

    /** Stays inside our own territory if any safe move lands there; otherwise heads for the nearest known owned cell. */
    private Direction pickBack(GameApi game) {
        List<Direction> candidates = safeDirections(game);
        List<Direction> withinTerritory = candidates.stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (!withinTerritory.isEmpty()) {
            return chooseRandom(game, withinTerritory);
        }
        GridPosition target = nearestKnownSelfTerritory(game).orElse(game.getRespawnPosition());
        return chooseBest(game, candidates, distanceTo(game, target)
                .thenComparingInt(direction -> direction == outDirection ? 1 : 0));
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

    private List<Direction> closestApproachDirections(GameApi game, List<Direction> candidates) {
        List<Direction> closest = new ArrayList<>();
        int closestDistance = OUT_LOOKAHEAD + 1;
        for (Direction direction : candidates) {
            int distance = distanceToOutsideTerritory(game, direction);
            if (distance < closestDistance) {
                closest.clear();
                closestDistance = distance;
            }
            if (distance == closestDistance && distance <= OUT_LOOKAHEAD) {
                closest.add(direction);
            }
        }
        return preferVertical(closest);
    }

    private int distanceToOutsideTerritory(GameApi game, Direction direction) {
        GridPosition position = game.getAgentPosition();
        for (int distance = 1; distance <= OUT_LOOKAHEAD; distance++) {
            position = MovementUtils.nextPosition(position, direction);
            if (!MovementUtils.isWithinBoard(position, game.getBoardWidth(), game.getBoardHeight())) {
                break;
            }
            Optional<VisibleCell> cell = MovementUtils.findCell(game.getVisibleGrid(), position);
            if (cell.isEmpty()) {
                break;
            }
            if (cell.get().territory() != TerritoryView.SELF) {
                return distance;
            }
        }
        return OUT_LOOKAHEAD + 1;
    }

    private List<Direction> preferVertical(List<Direction> candidates) {
        List<Direction> vertical = candidates.stream().filter(SafetyGridStateMachine::isVertical).toList();
        return vertical.isEmpty() ? candidates : vertical;
    }

    /** {@code false} for cells outside the visible window. */
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

    private Direction chooseRandom(GameApi game, List<Direction> candidates) {
        return candidates.isEmpty() ? fallback(game) : candidates.get(random.nextInt(candidates.size()));
    }

    private Comparator<Direction> distanceTo(GameApi game, GridPosition target) {
        return Comparator.comparingInt(direction -> MovementUtils.manhattanDistance(destination(game, direction), target));
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

    private boolean isOpponentVisible(GameApi game) {
        return findOpponentPosition(game).isPresent();
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

    private Direction fallback(GameApi game) {
        List<Direction> valid = MovementUtils.validDirections(game);
        return valid.isEmpty() ? Direction.NORTH : valid.get(0);
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

}
