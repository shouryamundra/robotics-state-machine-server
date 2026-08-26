package territorygame;

import territorygame.api.GridPosition;
import territorygame.domain.Agent;
import territorygame.domain.Board;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;

import java.util.List;

/** Test-only factory for small, deterministic two-player game states. */
public final class TestGames {

    private TestGames() {
    }

    public static GameState twoPlayerState(
            int width, int height,
            GridPosition position0, List<GridPosition> territory0,
            GridPosition position1, List<GridPosition> territory1,
            int turnsPerPlayer
    ) {
        Board board = new Board(width, height);
        PlayerId id0 = new PlayerId(0);
        PlayerId id1 = new PlayerId(1);

        Agent agent0 = new Agent(position0, position0);
        Agent agent1 = new Agent(position1, position1);
        Player player0 = new Player(id0, agent0, territory0);
        Player player1 = new Player(id1, agent1, territory1);

        for (GridPosition cell : territory0) {
            board.setTerritoryOwner(cell, id0);
        }
        for (GridPosition cell : territory1) {
            board.setTerritoryOwner(cell, id1);
        }

        return new GameState(board, List.of(player0, player1), turnsPerPlayer);
    }
}
