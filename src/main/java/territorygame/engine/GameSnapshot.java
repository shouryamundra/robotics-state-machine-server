package territorygame.engine;

import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.domain.PlayerId;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, fully copied view of match state for observers (the GUI).
 * Contains no behavior beyond display-derived values, and is safe to hand
 * across threads.
 *
 * <p>{@code errorMessage} is non-null exactly when the most recent turn (or
 * the engine itself) failed in some way worth surfacing to the user; it is
 * display-only and never affects match state.
 */
public record GameSnapshot(
        int width,
        int height,
        CellSnapshot[][] cells,
        List<PlayerSnapshot> players,
        PlayerId activePlayerId,
        MoveResult lastMoveResult,
        int visibilityWindowSize,
        boolean gameOver,
        String errorMessage
) {
    public GameSnapshot {
        CellSnapshot[][] copy = new CellSnapshot[cells.length][];
        for (int y = 0; y < cells.length; y++) {
            copy[y] = Arrays.copyOf(cells[y], cells[y].length);
        }
        cells = copy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameSnapshot that)) {
            return false;
        }
        return width == that.width
                && height == that.height
                && gameOver == that.gameOver
                && visibilityWindowSize == that.visibilityWindowSize
                && Arrays.deepEquals(cells, that.cells)
                && Objects.equals(players, that.players)
                && Objects.equals(activePlayerId, that.activePlayerId)
                && lastMoveResult == that.lastMoveResult
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                width, height, players, activePlayerId, lastMoveResult, visibilityWindowSize, gameOver, errorMessage);
        return 31 * result + Arrays.deepHashCode(cells);
    }

    @Override
    public String toString() {
        return "GameSnapshot[width=" + width + ", height=" + height
                + ", cells=" + Arrays.deepToString(cells)
                + ", players=" + players + ", activePlayerId=" + activePlayerId
                + ", lastMoveResult=" + lastMoveResult + ", visibilityWindowSize=" + visibilityWindowSize
                + ", gameOver=" + gameOver + ", errorMessage=" + errorMessage + "]";
    }

    /** Territory/trail ownership of one cell; either owner may be null. */
    public record CellSnapshot(PlayerId territoryOwner, PlayerId trailOwner) {
    }

    /** One player's displayable state at the moment of the snapshot. */
    public record PlayerSnapshot(
            PlayerId id,
            GridPosition position,
            int territoryCount,
            int killCount,
            int deathCount,
            int remainingTurns,
            List<GridPosition> trail
    ) {
        private static final int KILL_SCORE_BONUS = 10;

        /** Display-only composite score; the win condition still uses territoryCount alone. */
        public int score() {
            return territoryCount + killCount * KILL_SCORE_BONUS;
        }
    }
}
