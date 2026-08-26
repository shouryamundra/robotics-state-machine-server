package territorygame.api;

import java.util.List;

/**
 * Player-scoped view of the game a controller uses to observe state and
 * move. Implementations must never leak authoritative backend types or
 * mutable references.
 */
public interface GameApi {

    GridPosition getAgentPosition();

    GridPosition getRespawnPosition();

    int getOwnedTerritoryCellCount();

    int getOpponentTerritoryCellCount();

    int getRemainingTurns();

    /** The player's current active trail, ordered oldest to newest. */
    List<GridPosition> getActiveTrail();

    /** Cells visible around the agent, indexed [row][column] i.e. [y][x] within the window. */
    VisibleCell[][] getVisibleGrid();

    int getBoardWidth();

    int getBoardHeight();

    MoveResult move(Direction direction);
}
