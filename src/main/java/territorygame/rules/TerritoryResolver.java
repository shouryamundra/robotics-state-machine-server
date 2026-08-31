package territorygame.rules;

import territorygame.api.Direction;
import territorygame.api.GridPosition;
import territorygame.domain.Agent;
import territorygame.domain.Board;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;
import territorygame.helpers.MovementUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies capture effects when a player's trail closes: converting the
 * trail to territory and flood-filling only the region that trail newly
 * encloses. Existing holes in the capturer's territory (for example an
 * opponent island created by respawn) are left untouched.
 */
public final class TerritoryResolver {

    public void applyCapture(GameState state, PlayerId capturerId) {
        Board board = state.getBoard();
        Player capturer = state.getPlayer(capturerId);
        Agent agent = capturer.getAgent();

        Set<GridPosition> alreadyEnclosed = new HashSet<>(findEnclosedCells(board, capturerId));

        List<GridPosition> trail = agent.getActiveTrail();
        for (GridPosition cell : trail) {
            board.setTerritoryOwner(cell, capturerId);
            board.setTrailOwner(cell, null);
        }

        for (GridPosition enclosedCell : findEnclosedCells(board, capturerId)) {
            if (!alreadyEnclosed.contains(enclosedCell)) {
                board.setTerritoryOwner(enclosedCell, capturerId);
            }
        }

        agent.clearTrail();
    }

    private List<GridPosition> findEnclosedCells(Board board, PlayerId capturerId) {
        int width = board.getWidth();
        int height = board.getHeight();
        boolean[][] reachedFromEdge = new boolean[height][width];
        Deque<GridPosition> frontier = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            seedIfOutsideTerritory(board, capturerId, new GridPosition(x, 0), reachedFromEdge, frontier);
            seedIfOutsideTerritory(board, capturerId, new GridPosition(x, height - 1), reachedFromEdge, frontier);
        }
        for (int y = 0; y < height; y++) {
            seedIfOutsideTerritory(board, capturerId, new GridPosition(0, y), reachedFromEdge, frontier);
            seedIfOutsideTerritory(board, capturerId, new GridPosition(width - 1, y), reachedFromEdge, frontier);
        }

        while (!frontier.isEmpty()) {
            GridPosition current = frontier.poll();
            for (GridPosition neighbor : cardinalNeighbors(current, width, height)) {
                if (!reachedFromEdge[neighbor.y()][neighbor.x()]
                        && !capturerId.equals(board.territoryOwnerAt(neighbor))) {
                    reachedFromEdge[neighbor.y()][neighbor.x()] = true;
                    frontier.add(neighbor);
                }
            }
        }

        List<GridPosition> enclosed = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridPosition position = new GridPosition(x, y);
                if (!reachedFromEdge[y][x] && !capturerId.equals(board.territoryOwnerAt(position))) {
                    enclosed.add(position);
                }
            }
        }
        return enclosed;
    }

    private void seedIfOutsideTerritory(
            Board board, PlayerId capturerId, GridPosition position,
            boolean[][] reachedFromEdge, Deque<GridPosition> frontier
    ) {
        if (!capturerId.equals(board.territoryOwnerAt(position)) && !reachedFromEdge[position.y()][position.x()]) {
            reachedFromEdge[position.y()][position.x()] = true;
            frontier.add(position);
        }
    }

    private List<GridPosition> cardinalNeighbors(GridPosition position, int width, int height) {
        List<GridPosition> neighbors = new ArrayList<>(4);
        for (Direction direction : Direction.values()) {
            GridPosition neighbor = MovementUtils.nextPosition(position, direction);
            if (MovementUtils.isWithinBoard(neighbor, width, height)) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }
}
