package territorygame.engine;

import territorygame.api.AgentController;
import territorygame.api.MoveResult;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;

import java.util.List;
import java.util.Map;

/**
 * Executes exactly one turn for the current active player, re-invoking
 * their controller until a successful move is made, then advances to the
 * next player with turns remaining.
 *
 * <p>A controller that throws, or that never manages a successful move
 * within {@code maxAttemptsPerTurn} attempts, forfeits just that turn rather
 * than hanging the match: the reason is recorded on {@link GameState} for
 * the GUI/console to surface, and play continues with the next player.
 */
final class TurnManager {

    private final int maxAttemptsPerTurn;

    TurnManager(int maxAttemptsPerTurn) {
        this.maxAttemptsPerTurn = maxAttemptsPerTurn;
    }

    void executeTurn(GameState state, Map<PlayerId, AgentController> controllers, Map<PlayerId, GameApiImpl> apis) {
        PlayerId activeId = state.getActivePlayerId();
        GameApiImpl api = apis.get(activeId);
        AgentController controller = controllers.get(activeId);

        api.resetForNewTurn();
        state.setLastTurnError(null);
        int attempts = 0;
        while (!api.wasMoveMadeThisTurn() && attempts < maxAttemptsPerTurn) {
            attempts++;
            try {
                controller.takeTurn(api);
            } catch (RuntimeException e) {
                state.setLastTurnError("Player " + activeId.index() + "'s turn failed: " + e);
                break;
            }
        }

        if (api.wasMoveMadeThisTurn()) {
            state.setLastMoveResult(api.getLastResultThisTurn());
        } else {
            state.decrementRemainingTurns(activeId);
            state.setLastMoveResult(MoveResult.INVALID);
            if (state.getLastTurnError() == null) {
                state.setLastTurnError("Player " + activeId.index()
                        + "'s controller made no successful move after " + maxAttemptsPerTurn + " attempts");
            }
        }
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
