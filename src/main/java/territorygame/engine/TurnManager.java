package territorygame.engine;

import territorygame.api.AgentController;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;

import java.util.List;
import java.util.Map;

/**
 * Executes exactly one turn for the current active player, re-invoking
 * their controller until a successful move is made, then advances to the
 * next player with turns remaining.
 */
final class TurnManager {

    void executeTurn(GameState state, Map<PlayerId, AgentController> controllers, Map<PlayerId, GameApiImpl> apis) {
        PlayerId activeId = state.getActivePlayerId();
        GameApiImpl api = apis.get(activeId);
        AgentController controller = controllers.get(activeId);

        api.resetForNewTurn();
        do {
            controller.takeTurn(api);
        } while (!api.wasMoveMadeThisTurn());

        state.setLastMoveResult(api.getLastResultThisTurn());
        advanceActivePlayer(state);
    }

    private void advanceActivePlayer(GameState state) {
        List<Player> players = state.getPlayers();
        int currentIndex = indexOf(players, state.getActivePlayerId());
        for (int offset = 1; offset <= players.size(); offset++) {
            PlayerId candidate = players.get((currentIndex + offset) % players.size()).getId();
            if (state.getRemainingTurns(candidate) > 0) {
                state.setActivePlayerId(candidate);
                return;
            }
        }
        // No player has turns remaining; leave activePlayerId as-is, the
        // engine's run loop stops once state.isGameOver() is true.
    }

    private int indexOf(List<Player> players, PlayerId id) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("Active player not found: " + id);
    }
}
