package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.OccupantView;
import territorygame.api.TerritoryView;
import territorygame.api.VisibleCell;
import territorygame.helpers.MovementUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    private static final double TERRITORY_VISIBLE_THRESHOLD = 0.90;
    private static final int VERTICAL_WEIGHT = 2;

    private enum Phase {
        ATTACK, AVOID, REPOSITION, OUT, ACROSS, BACK
    }

    private Phase phase = Phase.OUT;

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
        } else if (game.getActiveTrail().isEmpty() && visibleTerritoryFraction(game) >= TERRITORY_VISIBLE_THRESHOLD) {
            phase = Phase.REPOSITION;
            direction = pickReposition(game);
        } else {
            direction = fallback(game); // replaced with pickExpedition(game) in Task 4
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
        return chooseBest(game, territoryOnly, farthestFirst.thenComparing(mostOpenFirst(game)));
    }

    /** Walks toward the opponent's half, preferring moves that stay on our own territory (so no trail risk is taken). */
    private Direction pickReposition(GameApi game) {
        Direction towardEnemy = enemyHalfDirection(game.getRespawnPosition(), game.getBoardWidth());
        List<Direction> candidates = safeDirections(game);
        List<Direction> territoryOnly = candidates.stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (!territoryOnly.isEmpty()) {
            candidates = territoryOnly;
        }
        if (candidates.contains(towardEnemy)) {
            return towardEnemy;
        }
        return chooseBest(game, candidates, mostOpenFirst(game));
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
        return isVertical(direction) ? count * VERTICAL_WEIGHT : count;
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
