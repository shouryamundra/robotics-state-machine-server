package territorygame.domain;

import territorygame.api.MoveResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative mutable match state: board, players, per-player remaining
 * turns, and whose turn it is. Never exposed to candidate or GUI code
 * directly. Assumes exactly two players, matching every rule in the spec
 * that refers to "the other agent" / "the opponent".
 */
public final class GameState {

    private final Board board;
    private final List<Player> players;
    private final Map<PlayerId, Integer> remainingTurns = new HashMap<>();
    private PlayerId activePlayerId;
    private MoveResult lastMoveResult;

    public GameState(Board board, List<Player> players, int turnsPerPlayer) {
        this.board = board;
        this.players = List.copyOf(players);
        for (Player player : players) {
            remainingTurns.put(player.getId(), turnsPerPlayer);
        }
        this.activePlayerId = players.get(0).getId();
    }

    public Board getBoard() {
        return board;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Player getPlayer(PlayerId id) {
        return players.stream()
                .filter(player -> player.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + id));
    }

    /** The other participant in this two-player match. */
    public Player getOpponent(PlayerId id) {
        return players.stream()
                .filter(player -> !player.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No opponent for: " + id));
    }

    public PlayerId getActivePlayerId() {
        return activePlayerId;
    }

    public void setActivePlayerId(PlayerId id) {
        this.activePlayerId = id;
    }

    public int getRemainingTurns(PlayerId id) {
        return remainingTurns.get(id);
    }

    public void decrementRemainingTurns(PlayerId id) {
        remainingTurns.merge(id, -1, Integer::sum);
    }

    public boolean isGameOver() {
        return remainingTurns.values().stream().allMatch(turns -> turns <= 0);
    }

    public MoveResult getLastMoveResult() {
        return lastMoveResult;
    }

    public void setLastMoveResult(MoveResult result) {
        this.lastMoveResult = result;
    }
}
