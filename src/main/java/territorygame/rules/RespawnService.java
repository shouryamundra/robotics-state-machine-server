package territorygame.rules;

import territorygame.api.GridPosition;
import territorygame.domain.Agent;
import territorygame.domain.Board;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;
import territorygame.helpers.MovementUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Owns the complete death/reset operation for a player: clearing their
 * trail and territory, restoring starting territory, and repositioning
 * their agent.
 */
public final class RespawnService {

    public void respawn(GameState state, PlayerId playerId) {
        Board board = state.getBoard();
        Player player = state.getPlayer(playerId);
        Agent agent = player.getAgent();

        for (GridPosition trailCell : agent.getActiveTrail()) {
            board.setTrailOwner(trailCell, null);
        }
        agent.clearTrail();

        board.clearAllTerritoryOf(playerId);
        for (GridPosition cell : player.getStartingTerritory()) {
            board.setTerritoryOwner(cell, playerId);
        }

        agent.setPosition(choosePosition(state, playerId));
    }

    private GridPosition choosePosition(GameState state, PlayerId playerId) {
        Player player = state.getPlayer(playerId);
        GridPosition respawnPosition = player.getAgent().getRespawnPosition();
        GridPosition opponentPosition = state.getOpponent(playerId).getAgent().getPosition();

        if (!respawnPosition.equals(opponentPosition)) {
            return respawnPosition;
        }

        Optional<GridPosition> withinStartingTerritory = player.getStartingTerritory().stream()
                .filter(cell -> !cell.equals(opponentPosition))
                .min(nearestToRespawn(respawnPosition));
        if (withinStartingTerritory.isPresent()) {
            return withinStartingTerritory.get();
        }

        // Starting territory is fully blocked (e.g. a 1-cell starting territory
        // with the opponent standing on it): fall back to the nearest free cell
        // anywhere on the board rather than failing.
        Board board = state.getBoard();
        return allPositions(board.getWidth(), board.getHeight()).stream()
                .filter(cell -> !cell.equals(opponentPosition))
                .min(nearestToRespawn(respawnPosition))
                .orElseThrow(() -> new IllegalStateException(
                        "No unoccupied cell available anywhere on the board for " + playerId));
    }

    private Comparator<GridPosition> nearestToRespawn(GridPosition respawnPosition) {
        return Comparator.comparingInt((GridPosition cell) -> MovementUtils.manhattanDistance(cell, respawnPosition))
                .thenComparingInt(GridPosition::y)
                .thenComparingInt(GridPosition::x);
    }

    private List<GridPosition> allPositions(int width, int height) {
        List<GridPosition> positions = new ArrayList<>(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                positions.add(new GridPosition(x, y));
            }
        }
        return positions;
    }
}
