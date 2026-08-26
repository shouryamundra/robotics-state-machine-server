package territorygame.helpers;

import org.junit.jupiter.api.Test;
import territorygame.api.CellViewType;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.api.VisibleCell;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementUtilsTest {

    @Test
    void nextPositionMovesOneCellPerDirection() {
        GridPosition start = new GridPosition(5, 5);

        assertEquals(new GridPosition(5, 4), MovementUtils.nextPosition(start, Direction.NORTH));
        assertEquals(new GridPosition(5, 6), MovementUtils.nextPosition(start, Direction.SOUTH));
        assertEquals(new GridPosition(6, 5), MovementUtils.nextPosition(start, Direction.EAST));
        assertEquals(new GridPosition(4, 5), MovementUtils.nextPosition(start, Direction.WEST));
    }

    @Test
    void isWithinBoardChecksBothAxes() {
        assertTrue(MovementUtils.isWithinBoard(new GridPosition(0, 0), 5, 5));
        assertTrue(MovementUtils.isWithinBoard(new GridPosition(4, 4), 5, 5));
        assertFalse(MovementUtils.isWithinBoard(new GridPosition(-1, 0), 5, 5));
        assertFalse(MovementUtils.isWithinBoard(new GridPosition(5, 0), 5, 5));
        assertFalse(MovementUtils.isWithinBoard(new GridPosition(0, 5), 5, 5));
    }

    @Test
    void isValidBoardMoveChecksDestinationBounds() {
        assertTrue(MovementUtils.isValidBoardMove(new GridPosition(0, 0), Direction.EAST, 5, 5));
        assertFalse(MovementUtils.isValidBoardMove(new GridPosition(0, 0), Direction.NORTH, 5, 5));
        assertFalse(MovementUtils.isValidBoardMove(new GridPosition(0, 0), Direction.WEST, 5, 5));
    }

    @Test
    void manhattanDistanceIsGridDistance() {
        assertEquals(7, MovementUtils.manhattanDistance(new GridPosition(0, 0), new GridPosition(3, 4)));
        assertEquals(0, MovementUtils.manhattanDistance(new GridPosition(2, 2), new GridPosition(2, 2)));
    }

    @Test
    void isValidMoveRejectsOutOfBoundsWithoutNeedingVisibleGrid() {
        StubGameApi game = new StubGameApi(new GridPosition(0, 0), 5, 5, new VisibleCell[0][0]);

        assertFalse(MovementUtils.isValidMove(game, Direction.NORTH));
    }

    @Test
    void isValidMoveRejectsOpponentAgentCell() {
        GridPosition position = new GridPosition(2, 2);
        GridPosition opponentAt = new GridPosition(3, 2);
        VisibleCell[][] grid = {{
                new VisibleCell(opponentAt, CellViewType.OPPONENT_AGENT)
        }};
        StubGameApi game = new StubGameApi(position, 5, 5, grid);

        assertFalse(MovementUtils.isValidMove(game, Direction.EAST));
    }

    @Test
    void isValidMoveAcceptsFreeInBoundsCell() {
        GridPosition position = new GridPosition(2, 2);
        GridPosition destination = new GridPosition(3, 2);
        VisibleCell[][] grid = {{
                new VisibleCell(destination, CellViewType.FREE)
        }};
        StubGameApi game = new StubGameApi(position, 5, 5, grid);

        assertTrue(MovementUtils.isValidMove(game, Direction.EAST));
    }

    @Test
    void findCellReturnsTheCellAtAMatchingPosition() {
        GridPosition target = new GridPosition(3, 2);
        VisibleCell[][] grid = {{new VisibleCell(target, CellViewType.OPPONENT_TERRITORY)}};

        Optional<VisibleCell> found = MovementUtils.findCell(grid, target);

        assertTrue(found.isPresent());
        assertEquals(CellViewType.OPPONENT_TERRITORY, found.get().type());
    }

    @Test
    void findCellReturnsEmptyWhenPositionIsNotInTheGrid() {
        VisibleCell[][] grid = {{new VisibleCell(new GridPosition(3, 2), CellViewType.FREE)}};

        assertTrue(MovementUtils.findCell(grid, new GridPosition(9, 9)).isEmpty());
    }

    @Test
    void validDirectionsExcludesOutOfBoundsAndOpponentAgentCells() {
        GridPosition position = new GridPosition(0, 0);
        GridPosition east = new GridPosition(1, 0);
        GridPosition south = new GridPosition(0, 1);
        VisibleCell[][] grid = {
                {new VisibleCell(position, CellViewType.SELF_AGENT), new VisibleCell(east, CellViewType.OPPONENT_AGENT)},
                {new VisibleCell(south, CellViewType.FREE), new VisibleCell(new GridPosition(1, 1), CellViewType.FREE)}
        };
        StubGameApi game = new StubGameApi(position, 5, 5, grid);

        List<Direction> valid = MovementUtils.validDirections(game);

        assertEquals(Set.of(Direction.SOUTH), Set.copyOf(valid));
    }

    @Test
    void randomDirectionReturnsOneOfTheFourDirections() {
        Direction direction = MovementUtils.randomDirection(new Random(1));

        assertTrue(Set.of(Direction.values()).contains(direction));
    }

    /** Minimal GameApi test double exposing only what MovementUtils reads. */
    private static final class StubGameApi implements GameApi {
        private final GridPosition position;
        private final int width;
        private final int height;
        private final VisibleCell[][] visibleGrid;

        StubGameApi(GridPosition position, int width, int height, VisibleCell[][] visibleGrid) {
            this.position = position;
            this.width = width;
            this.height = height;
            this.visibleGrid = visibleGrid;
        }

        @Override
        public GridPosition getAgentPosition() {
            return position;
        }

        @Override
        public GridPosition getRespawnPosition() {
            return position;
        }

        @Override
        public int getOwnedTerritoryCellCount() {
            return 0;
        }

        @Override
        public int getOpponentTerritoryCellCount() {
            return 0;
        }

        @Override
        public int getRemainingTurns() {
            return 0;
        }

        @Override
        public List<GridPosition> getActiveTrail() {
            return List.of();
        }

        @Override
        public VisibleCell[][] getVisibleGrid() {
            return visibleGrid;
        }

        @Override
        public int getBoardWidth() {
            return width;
        }

        @Override
        public int getBoardHeight() {
            return height;
        }

        @Override
        public MoveResult move(Direction direction) {
            return MoveResult.INVALID;
        }
    }
}
