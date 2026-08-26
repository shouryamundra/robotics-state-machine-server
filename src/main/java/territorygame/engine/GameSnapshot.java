package territorygame.engine;

import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.domain.PlayerId;

import java.util.List;

/**
 * Immutable, fully copied view of match state for observers (the GUI).
 * Contains no behavior and is safe to hand across threads.
 */
public record GameSnapshot(
        int width,
        int height,
        CellSnapshot[][] cells,
        List<PlayerSnapshot> players,
        PlayerId activePlayerId,
        MoveResult lastMoveResult,
        int visibilityWindowSize,
        boolean gameOver
) {
    /** Territory/trail ownership of one cell; either owner may be null. */
    public record CellSnapshot(PlayerId territoryOwner, PlayerId trailOwner) {
    }

    /** One player's displayable state at the moment of the snapshot. */
    public record PlayerSnapshot(
            PlayerId id,
            GridPosition position,
            int territoryCount,
            int remainingTurns,
            List<GridPosition> trail
    ) {
    }
}
