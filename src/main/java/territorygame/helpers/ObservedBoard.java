package territorygame.helpers;

import territorygame.api.CellViewType;
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
    private final CellViewType[][] observed;

    public ObservedBoard(int width, int height) {
        this.width = width;
        this.height = height;
        this.observed = new CellViewType[height][width];
    }

    public void update(VisibleCell[][] visibleGrid) {
        for (VisibleCell[] row : visibleGrid) {
            for (VisibleCell cell : row) {
                GridPosition position = cell.position();
                observed[position.y()][position.x()] = cell.type();
            }
        }
    }

    public Optional<CellViewType> get(GridPosition position) {
        if (!isWithinBounds(position)) {
            return Optional.empty();
        }
        return Optional.ofNullable(observed[position.y()][position.x()]);
    }

    public boolean hasObserved(GridPosition position) {
        return isWithinBounds(position) && observed[position.y()][position.x()] != null;
    }

    public void clear() {
        for (CellViewType[] row : observed) {
            java.util.Arrays.fill(row, null);
        }
    }

    private boolean isWithinBounds(GridPosition position) {
        return position.x() >= 0 && position.x() < width
                && position.y() >= 0 && position.y() < height;
    }
}
