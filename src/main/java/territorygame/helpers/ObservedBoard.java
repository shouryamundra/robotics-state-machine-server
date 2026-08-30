package territorygame.helpers;

import territorygame.api.GridPosition;
import territorygame.api.VisibleCell;

import java.util.Optional;

/**
 * A candidate-side memory of previously observed cells. Stores only the
 * latest value seen for each cell; never infers changes to cells that
 * haven't been re-observed.
 */
public final class ObservedBoard {

    private final int width;
    private final int height;
    private final VisibleCell[][] observed;

    public ObservedBoard(int width, int height) {
        this.width = width;
        this.height = height;
        this.observed = new VisibleCell[height][width];
    }

    public void update(VisibleCell[][] visibleGrid) {
        for (VisibleCell[] row : visibleGrid) {
            for (VisibleCell cell : row) {
                GridPosition position = cell.position();
                observed[position.y()][position.x()] = cell;
            }
        }
    }

    public Optional<VisibleCell> get(GridPosition position) {
        if (!MovementUtils.isWithinBoard(position, width, height)) {
            return Optional.empty();
        }
        return Optional.ofNullable(observed[position.y()][position.x()]);
    }

    public boolean hasObserved(GridPosition position) {
        return MovementUtils.isWithinBoard(position, width, height) && observed[position.y()][position.x()] != null;
    }

    public void clear() {
        for (VisibleCell[] row : observed) {
            java.util.Arrays.fill(row, null);
        }
    }
}
