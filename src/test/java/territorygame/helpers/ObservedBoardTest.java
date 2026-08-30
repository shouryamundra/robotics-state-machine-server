package territorygame.helpers;

import org.junit.jupiter.api.Test;
import territorygame.api.GridPosition;
import territorygame.api.OccupantView;
import territorygame.api.TerritoryView;
import territorygame.api.VisibleCell;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservedBoardTest {

    @Test
    void unobservedCellHasNoValue() {
        ObservedBoard board = new ObservedBoard(5, 5);
        GridPosition position = new GridPosition(1, 1);

        assertFalse(board.hasObserved(position));
        assertEquals(Optional.empty(), board.get(position));
    }

    @Test
    void updateStoresLatestValuePerCell() {
        ObservedBoard board = new ObservedBoard(5, 5);
        GridPosition position = new GridPosition(1, 1);
        VisibleCell stored = new VisibleCell(position, OccupantView.EMPTY, TerritoryView.SELF);
        VisibleCell[][] grid = {{stored}};

        board.update(grid);

        assertTrue(board.hasObserved(position));
        assertEquals(Optional.of(stored), board.get(position));
    }

    @Test
    void laterUpdateOverwritesEarlierValueForSameCell() {
        ObservedBoard board = new ObservedBoard(5, 5);
        GridPosition position = new GridPosition(1, 1);
        VisibleCell later = new VisibleCell(position, OccupantView.EMPTY, TerritoryView.OPPONENT);

        board.update(new VisibleCell[][]{{new VisibleCell(position, OccupantView.EMPTY, TerritoryView.UNOWNED)}});
        board.update(new VisibleCell[][]{{later}});

        assertEquals(Optional.of(later), board.get(position));
    }

    @Test
    void updateDoesNotAffectCellsOutsideTheGivenGrid() {
        ObservedBoard board = new ObservedBoard(5, 5);
        GridPosition observed = new GridPosition(1, 1);
        GridPosition untouched = new GridPosition(3, 3);
        board.update(new VisibleCell[][]{{new VisibleCell(observed, OccupantView.EMPTY, TerritoryView.SELF)}});

        assertFalse(board.hasObserved(untouched));
    }

    @Test
    void clearForgetsAllPreviouslyObservedCells() {
        ObservedBoard board = new ObservedBoard(5, 5);
        GridPosition position = new GridPosition(1, 1);
        board.update(new VisibleCell[][]{{new VisibleCell(position, OccupantView.EMPTY, TerritoryView.SELF)}});

        board.clear();

        assertFalse(board.hasObserved(position));
    }

    @Test
    void getReturnsEmptyForOutOfBoundsPosition() {
        ObservedBoard board = new ObservedBoard(5, 5);

        assertEquals(Optional.empty(), board.get(new GridPosition(-1, 0)));
        assertFalse(board.hasObserved(new GridPosition(10, 10)));
    }
}
