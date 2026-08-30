package candidate.examples;

import org.junit.jupiter.api.Test;
import territorygame.api.Direction;
import territorygame.api.GridPosition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyGridStateMachineTest {

    @Test
    void chebyshevDistanceIsTheLargerAxisDelta() {
        assertEquals(3, SafetyGridStateMachine.chebyshevDistance(new GridPosition(0, 0), new GridPosition(3, 1)));
        assertEquals(2, SafetyGridStateMachine.chebyshevDistance(new GridPosition(5, 5), new GridPosition(4, 3)));
        assertEquals(0, SafetyGridStateMachine.chebyshevDistance(new GridPosition(2, 2), new GridPosition(2, 2)));
    }

    @Test
    void emptyTrailAlwaysFitsRegardlessOfGridSize() {
        assertTrue(SafetyGridStateMachine.fitsSafetyGrid(new GridPosition(10, 10), List.of(), 1));
    }

    @Test
    void trailFitsWhenEveryCellIsWithinHalfTheGridSizeOfTheHead() {
        GridPosition head = new GridPosition(5, 5);
        List<GridPosition> trail = List.of(new GridPosition(5, 4), new GridPosition(5, 5));

        assertTrue(SafetyGridStateMachine.fitsSafetyGrid(head, trail, 5)); // half = 2, distance = 1
    }

    @Test
    void trailDoesNotFitWhenAnyCellExceedsHalfTheGridSize() {
        GridPosition head = new GridPosition(5, 5);
        List<GridPosition> trail = List.of(new GridPosition(2, 5), new GridPosition(5, 5)); // distance 3

        assertFalse(SafetyGridStateMachine.fitsSafetyGrid(head, trail, 5)); // half = 2
    }

    @Test
    void oppositeReturnsTheReverseCardinalDirection() {
        assertEquals(Direction.SOUTH, SafetyGridStateMachine.opposite(Direction.NORTH));
        assertEquals(Direction.NORTH, SafetyGridStateMachine.opposite(Direction.SOUTH));
        assertEquals(Direction.WEST, SafetyGridStateMachine.opposite(Direction.EAST));
        assertEquals(Direction.EAST, SafetyGridStateMachine.opposite(Direction.WEST));
    }

    @Test
    void perpendicularOptionsForNorthSouthAreEastWest() {
        assertEquals(List.of(Direction.EAST, Direction.WEST), SafetyGridStateMachine.perpendicularOptions(Direction.NORTH));
        assertEquals(List.of(Direction.EAST, Direction.WEST), SafetyGridStateMachine.perpendicularOptions(Direction.SOUTH));
    }

    @Test
    void perpendicularOptionsForEastWestAreNorthSouth() {
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), SafetyGridStateMachine.perpendicularOptions(Direction.EAST));
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), SafetyGridStateMachine.perpendicularOptions(Direction.WEST));
    }

    @Test
    void mirrorBackWalksTheOppositeOfOutDirectionForStepsOutSteps() {
        GridPosition position = new GridPosition(5, 3); // 2 north, then 3 east of a (5,5) start
        GridPosition mirrored = SafetyGridStateMachine.mirrorBack(position, Direction.NORTH, 2);

        assertEquals(new GridPosition(5, 5), mirrored); // 2 steps south (opposite of north) from (5,3)
    }

    @Test
    void mirrorBackWithZeroStepsReturnsTheSamePosition() {
        GridPosition position = new GridPosition(7, 7);

        assertEquals(position, SafetyGridStateMachine.mirrorBack(position, Direction.EAST, 0));
    }

    @Test
    void isVerticalIsTrueOnlyForNorthAndSouth() {
        assertTrue(SafetyGridStateMachine.isVertical(Direction.NORTH));
        assertTrue(SafetyGridStateMachine.isVertical(Direction.SOUTH));
        assertFalse(SafetyGridStateMachine.isVertical(Direction.EAST));
        assertFalse(SafetyGridStateMachine.isVertical(Direction.WEST));
    }

    @Test
    void isOnSideOfChecksTheRelevantAxisOnly() {
        GridPosition agent = new GridPosition(5, 5);

        assertTrue(SafetyGridStateMachine.isOnSideOf(new GridPosition(5, 2), agent, Direction.NORTH));
        assertFalse(SafetyGridStateMachine.isOnSideOf(new GridPosition(5, 8), agent, Direction.NORTH));
        assertTrue(SafetyGridStateMachine.isOnSideOf(new GridPosition(9, 5), agent, Direction.EAST));
        assertFalse(SafetyGridStateMachine.isOnSideOf(new GridPosition(1, 5), agent, Direction.EAST));
    }

    @Test
    void isWestOfMidlineComparesXToHalfBoardWidth() {
        assertTrue(SafetyGridStateMachine.isWestOfMidline(new GridPosition(4, 0), 50));
        assertFalse(SafetyGridStateMachine.isWestOfMidline(new GridPosition(45, 0), 50));
    }

    @Test
    void isOnHomeHalfIsTrueWhenCurrentAndRespawnAreOnTheSameSide() {
        GridPosition respawn = new GridPosition(4, 25);

        assertTrue(SafetyGridStateMachine.isOnHomeHalf(new GridPosition(10, 25), respawn, 50));
        assertFalse(SafetyGridStateMachine.isOnHomeHalf(new GridPosition(40, 25), respawn, 50));
    }

    @Test
    void enemyHalfDirectionPointsAwayFromRespawnSide() {
        assertEquals(Direction.EAST, SafetyGridStateMachine.enemyHalfDirection(new GridPosition(4, 25), 50));
        assertEquals(Direction.WEST, SafetyGridStateMachine.enemyHalfDirection(new GridPosition(45, 25), 50));
    }
}
