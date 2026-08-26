package territorygame.visibility;

import territorygame.api.CellViewType;
import territorygame.api.GridPosition;
import territorygame.api.VisibleCell;
import territorygame.domain.Agent;
import territorygame.domain.Board;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;

/**
 * Produces candidate-facing observations from authoritative state, owning
 * the translation from internal player identities to relative
 * {@code SELF_*}/{@code OPPONENT_*} types.
 */
public final class VisibilityService {

    private final int windowSize;

    public VisibilityService(int windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * Returns a window of cells centered on {@code viewerId}'s agent,
     * clipped to the board, indexed [row][column] (y, then x).
     */
    public VisibleCell[][] computeVisibleGrid(GameState state, PlayerId viewerId) {
        Board board = state.getBoard();
        Player viewer = state.getPlayer(viewerId);
        Player opponent = state.getOpponent(viewerId);
        GridPosition center = viewer.getAgent().getPosition();

        int half = windowSize / 2;
        int minX = Math.max(0, center.x() - half);
        int maxX = Math.min(board.getWidth() - 1, center.x() + half);
        int minY = Math.max(0, center.y() - half);
        int maxY = Math.min(board.getHeight() - 1, center.y() + half);

        VisibleCell[][] grid = new VisibleCell[maxY - minY + 1][maxX - minX + 1];
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                GridPosition position = new GridPosition(x, y);
                CellViewType type = classify(board, position, viewer.getAgent(), opponent.getAgent(), viewerId, opponent.getId());
                grid[y - minY][x - minX] = new VisibleCell(position, type);
            }
        }
        return grid;
    }

    private CellViewType classify(
            Board board,
            GridPosition position,
            Agent viewerAgent,
            Agent opponentAgent,
            PlayerId viewerId,
            PlayerId opponentId
    ) {
        if (position.equals(viewerAgent.getPosition())) {
            return CellViewType.SELF_AGENT;
        }
        if (position.equals(opponentAgent.getPosition())) {
            return CellViewType.OPPONENT_AGENT;
        }

        PlayerId trailOwner = board.trailOwnerAt(position);
        if (viewerId.equals(trailOwner)) {
            return CellViewType.SELF_TRAIL;
        }
        if (opponentId.equals(trailOwner)) {
            return CellViewType.OPPONENT_TRAIL;
        }

        PlayerId territoryOwner = board.territoryOwnerAt(position);
        if (viewerId.equals(territoryOwner)) {
            return CellViewType.SELF_TERRITORY;
        }
        if (opponentId.equals(territoryOwner)) {
            return CellViewType.OPPONENT_TERRITORY;
        }

        return CellViewType.FREE;
    }
}
