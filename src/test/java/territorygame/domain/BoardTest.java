package territorygame.domain;

import org.junit.jupiter.api.Test;
import territorygame.api.GridPosition;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    private final PlayerId player0 = new PlayerId(0);
    private final PlayerId player1 = new PlayerId(1);

    @Test
    void newBoardHasNoOwnersAnywhere() {
        Board board = new Board(5, 5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                GridPosition position = new GridPosition(x, y);
                assertNull(board.territoryOwnerAt(position));
                assertNull(board.trailOwnerAt(position));
            }
        }
    }

    @Test
    void setAndReadTerritoryOwner() {
        Board board = new Board(5, 5);
        GridPosition position = new GridPosition(2, 2);

        board.setTerritoryOwner(position, player0);

        assertEquals(player0, board.territoryOwnerAt(position));
    }

    @Test
    void cellCanHaveTerritoryOwnerAndDifferentTrailOwnerAtOnce() {
        Board board = new Board(5, 5);
        GridPosition position = new GridPosition(2, 2);

        board.setTerritoryOwner(position, player0);
        board.setTrailOwner(position, player1);

        assertEquals(player0, board.territoryOwnerAt(position));
        assertEquals(player1, board.trailOwnerAt(position));
    }

    @Test
    void territoryCountReflectsOwnedCells() {
        Board board = new Board(5, 5);
        board.setTerritoryOwner(new GridPosition(0, 0), player0);
        board.setTerritoryOwner(new GridPosition(1, 0), player0);
        board.setTerritoryOwner(new GridPosition(2, 0), player1);

        assertEquals(2, board.territoryCount(player0));
        assertEquals(1, board.territoryCount(player1));
    }

    @Test
    void territoryOfReturnsExactOwnedSet() {
        Board board = new Board(5, 5);
        GridPosition a = new GridPosition(0, 0);
        GridPosition b = new GridPosition(1, 0);
        board.setTerritoryOwner(a, player0);
        board.setTerritoryOwner(b, player0);

        assertEquals(Set.of(a, b), board.territoryOf(player0));
    }

    @Test
    void reassigningTerritoryOwnerUpdatesBothOldAndNewCounts() {
        Board board = new Board(5, 5);
        GridPosition position = new GridPosition(0, 0);
        board.setTerritoryOwner(position, player0);

        board.setTerritoryOwner(position, player1);

        assertEquals(0, board.territoryCount(player0));
        assertEquals(1, board.territoryCount(player1));
    }

    @Test
    void clearAllTerritoryOfRemovesOnlyThatPlayersCells() {
        Board board = new Board(5, 5);
        GridPosition a = new GridPosition(0, 0);
        GridPosition b = new GridPosition(1, 0);
        board.setTerritoryOwner(a, player0);
        board.setTerritoryOwner(b, player1);

        board.clearAllTerritoryOf(player0);

        assertNull(board.territoryOwnerAt(a));
        assertEquals(player1, board.territoryOwnerAt(b));
    }

    @Test
    void isWithinBoundsRejectsNegativeAndOutOfRangeCoordinates() {
        Board board = new Board(5, 5);

        assertTrue(board.isWithinBounds(new GridPosition(0, 0)));
        assertTrue(board.isWithinBounds(new GridPosition(4, 4)));
        assertFalse(board.isWithinBounds(new GridPosition(-1, 0)));
        assertFalse(board.isWithinBounds(new GridPosition(5, 0)));
        assertFalse(board.isWithinBounds(new GridPosition(0, 5)));
    }
}
