package territorygame.rules;

import territorygame.api.GridPosition;
import territorygame.domain.Agent;
import territorygame.domain.Board;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;

import java.util.Comparator;

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

        return player.getStartingTerritory().stream()
                .filter(cell -> !cell.equals(opponentPosition))
                .min(Comparator.comparingInt((GridPosition cell) -> manhattanDistance(cell, respawnPosition))
                        .thenComparingInt(GridPosition::y)
                        .thenComparingInt(GridPosition::x))
                .orElseThrow(() -> new IllegalStateException(
                        "No unoccupied cell available in starting territory for " + playerId));
    }

    private int manhattanDistance(GridPosition a, GridPosition b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }
}
