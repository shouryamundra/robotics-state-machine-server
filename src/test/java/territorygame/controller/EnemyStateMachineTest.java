package territorygame.controller;

import org.junit.jupiter.api.Test;
import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.api.OccupantView;
import territorygame.api.TerritoryView;
import territorygame.api.VisibleCell;
import territorygame.domain.GameConfig;
import territorygame.engine.GameEngine;
import territorygame.engine.GameSnapshot;
import territorygame.helpers.MovementUtils;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyStateMachineTest {

    @Test
    void chebyshevDistanceIsTheLargerAxisDelta() {
        assertEquals(3, EnemyStateMachine.chebyshevDistance(new GridPosition(0, 0), new GridPosition(3, 1)));
        assertEquals(2, EnemyStateMachine.chebyshevDistance(new GridPosition(5, 5), new GridPosition(4, 3)));
        assertEquals(0, EnemyStateMachine.chebyshevDistance(new GridPosition(2, 2), new GridPosition(2, 2)));
    }

    @Test
    void emptyTrailAlwaysFitsRegardlessOfGridSize() {
        assertTrue(EnemyStateMachine.fitsSafetyGrid(new GridPosition(10, 10), List.of(), 1));
    }

    @Test
    void trailFitsWhenEveryCellIsWithinHalfTheGridSizeOfTheHead() {
        GridPosition head = new GridPosition(5, 5);
        List<GridPosition> trail = List.of(new GridPosition(5, 4), new GridPosition(5, 5));

        assertTrue(EnemyStateMachine.fitsSafetyGrid(head, trail, 5)); // half = 2, distance = 1
    }

    @Test
    void trailDoesNotFitWhenAnyCellExceedsHalfTheGridSize() {
        GridPosition head = new GridPosition(5, 5);
        List<GridPosition> trail = List.of(new GridPosition(2, 5), new GridPosition(5, 5)); // distance 3

        assertFalse(EnemyStateMachine.fitsSafetyGrid(head, trail, 5)); // half = 2
    }

    @Test
    void oppositeReturnsTheReverseCardinalDirection() {
        assertEquals(Direction.SOUTH, EnemyStateMachine.opposite(Direction.NORTH));
        assertEquals(Direction.NORTH, EnemyStateMachine.opposite(Direction.SOUTH));
        assertEquals(Direction.WEST, EnemyStateMachine.opposite(Direction.EAST));
        assertEquals(Direction.EAST, EnemyStateMachine.opposite(Direction.WEST));
    }

    @Test
    void perpendicularOptionsForNorthSouthAreEastWest() {
        assertEquals(List.of(Direction.EAST, Direction.WEST), EnemyStateMachine.perpendicularOptions(Direction.NORTH));
        assertEquals(List.of(Direction.EAST, Direction.WEST), EnemyStateMachine.perpendicularOptions(Direction.SOUTH));
    }

    @Test
    void perpendicularOptionsForEastWestAreNorthSouth() {
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), EnemyStateMachine.perpendicularOptions(Direction.EAST));
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), EnemyStateMachine.perpendicularOptions(Direction.WEST));
    }

    @Test
    void mirrorBackWalksTheOppositeOfOutDirectionForStepsOutSteps() {
        GridPosition position = new GridPosition(5, 3); // 2 north, then 3 east of a (5,5) start
        GridPosition mirrored = EnemyStateMachine.mirrorBack(position, Direction.NORTH, 2);

        assertEquals(new GridPosition(5, 5), mirrored); // 2 steps south (opposite of north) from (5,3)
    }

    @Test
    void mirrorBackWithZeroStepsReturnsTheSamePosition() {
        GridPosition position = new GridPosition(7, 7);

        assertEquals(position, EnemyStateMachine.mirrorBack(position, Direction.EAST, 0));
    }

    @Test
    void isVerticalIsTrueOnlyForNorthAndSouth() {
        assertTrue(EnemyStateMachine.isVertical(Direction.NORTH));
        assertTrue(EnemyStateMachine.isVertical(Direction.SOUTH));
        assertFalse(EnemyStateMachine.isVertical(Direction.EAST));
        assertFalse(EnemyStateMachine.isVertical(Direction.WEST));
    }

    @Test
    void repositionKeepsItsRandomDirectionWhileValid() {
        VisibleCell[][] grid = filledGrid(7, TerritoryView.SELF);
        StubGameApi game = new StubGameApi(new GridPosition(3, 3), new GridPosition(0, 3), grid);
        EnemyStateMachine controller = new EnemyStateMachine();

        controller.takeTurn(game);
        Direction committed = game.movedDirection;

        game.position = MovementUtils.nextPosition(game.position, committed);
        controller.takeTurn(game);

        assertEquals("REPOSITION", controller.getDebugState());
        assertEquals(committed, game.movedDirection);
    }

    @Test
    void repositionCommitsToNonSelfTerritoryWithinThreeCells() {
        VisibleCell[][] grid = filledGrid(7, TerritoryView.SELF);
        grid[3][6] = new VisibleCell(new GridPosition(6, 3), OccupantView.EMPTY, TerritoryView.OPPONENT);
        grid[2][4] = new VisibleCell(new GridPosition(4, 2), OccupantView.EMPTY, TerritoryView.UNOWNED);
        StubGameApi game = new StubGameApi(new GridPosition(3, 3), new GridPosition(0, 3), grid);
        EnemyStateMachine controller = new EnemyStateMachine();

        controller.takeTurn(game);
        assertEquals("REPOSITION", controller.getDebugState());
        assertEquals(Direction.EAST, game.movedDirection);

        game.position = new GridPosition(4, 3);
        controller.takeTurn(game);
        assertEquals("REPOSITION", controller.getDebugState());
        assertEquals(Direction.EAST, game.movedDirection);

        game.position = new GridPosition(5, 3);
        controller.takeTurn(game);
        assertEquals("OUT", controller.getDebugState());
        assertEquals(Direction.EAST, game.movedDirection);
    }

    @Test
    void repositionPrefersTheNearestNonSelfRay() {
        VisibleCell[][] grid = filledGrid(7, TerritoryView.SELF);
        grid[0][3] = new VisibleCell(new GridPosition(3, 0), OccupantView.EMPTY, TerritoryView.UNOWNED);
        grid[3][5] = new VisibleCell(new GridPosition(5, 3), OccupantView.EMPTY, TerritoryView.OPPONENT);
        StubGameApi game = new StubGameApi(new GridPosition(3, 3), new GridPosition(0, 3), grid);

        new EnemyStateMachine().takeTurn(game);

        assertEquals(Direction.EAST, game.movedDirection);
    }

    @Test
    void repositionPrefersVerticalWhenNearestRaysTie() {
        VisibleCell[][] grid = filledGrid(7, TerritoryView.SELF);
        grid[1][3] = new VisibleCell(new GridPosition(3, 1), OccupantView.EMPTY, TerritoryView.UNOWNED);
        grid[3][5] = new VisibleCell(new GridPosition(5, 3), OccupantView.EMPTY, TerritoryView.OPPONENT);
        StubGameApi game = new StubGameApi(new GridPosition(3, 3), new GridPosition(0, 3), grid);

        new EnemyStateMachine().takeTurn(game);

        assertEquals(Direction.NORTH, game.movedDirection);
    }

    @Test
    void outPrefersVerticalAmongAdjacentNonSelfDirections() {
        VisibleCell[][] grid = filledGrid(7, TerritoryView.SELF);
        grid[2][3] = new VisibleCell(new GridPosition(3, 2), OccupantView.EMPTY, TerritoryView.OPPONENT);
        grid[3][4] = new VisibleCell(new GridPosition(4, 3), OccupantView.EMPTY, TerritoryView.OPPONENT);
        for (int y = 3; y < 7; y++) {
            for (int x = 5; x < 7; x++) {
                grid[y][x] = new VisibleCell(new GridPosition(x, y), OccupantView.EMPTY, TerritoryView.UNOWNED);
            }
        }
        StubGameApi game = new StubGameApi(new GridPosition(3, 3), new GridPosition(0, 3), grid);

        new EnemyStateMachine().takeTurn(game);

        assertEquals(Direction.NORTH, game.movedDirection);
    }

    @Test
    void outCanStartOntoOpponentTerritory() {
        VisibleCell[][] grid = filledGrid(TerritoryView.SELF);
        for (int x = 0; x < 5; x++) {
            grid[0][x] = new VisibleCell(new GridPosition(x, 0), OccupantView.EMPTY, TerritoryView.UNOWNED);
        }
        grid[2][3] = new VisibleCell(new GridPosition(3, 2), OccupantView.EMPTY, TerritoryView.OPPONENT);
        StubGameApi game = new StubGameApi(new GridPosition(2, 2), new GridPosition(0, 2), grid);
        EnemyStateMachine controller = new EnemyStateMachine();

        controller.takeTurn(game);

        assertEquals("OUT", controller.getDebugState());
        assertEquals(Direction.EAST, game.movedDirection);
    }

    @Test
    void outStopsBeforeMovingBackOntoOwnedTerritory() {
        VisibleCell[][] grid = filledGrid(TerritoryView.SELF);
        grid[2][3] = new VisibleCell(new GridPosition(3, 2), OccupantView.EMPTY, TerritoryView.OPPONENT);
        StubGameApi game = new StubGameApi(new GridPosition(2, 2), new GridPosition(0, 2), grid);
        EnemyStateMachine controller = new EnemyStateMachine();

        controller.takeTurn(game);
        assertEquals(Direction.EAST, game.movedDirection);

        game.position = new GridPosition(3, 2);
        game.activeTrail = List.of(new GridPosition(2, 2));
        controller.takeTurn(game);

        assertEquals("ACROSS", controller.getDebugState());
        assertTrue(List.of(Direction.NORTH, Direction.SOUTH).contains(game.movedDirection));
    }

    @Test
    void acrossChoosesThePerpendicularDirectionWithAnOwnedMirror() {
        VisibleCell[][] grid = filledGrid(7, TerritoryView.SELF);
        grid[3][3] = new VisibleCell(new GridPosition(3, 3), OccupantView.EMPTY, TerritoryView.OPPONENT);
        StubGameApi game = new StubGameApi(new GridPosition(2, 3), new GridPosition(0, 3), grid);
        EnemyStateMachine controller = new EnemyStateMachine();

        controller.takeTurn(game);
        assertEquals(Direction.EAST, game.movedDirection);

        grid[2][2] = new VisibleCell(new GridPosition(2, 2), OccupantView.EMPTY, TerritoryView.UNOWNED);
        game.position = new GridPosition(3, 3);
        game.activeTrail = List.of(new GridPosition(3, 3));
        controller.takeTurn(game);

        assertEquals("ACROSS", controller.getDebugState());
        assertEquals(Direction.SOUTH, game.movedDirection);
    }

    @Test
    void backRetracesOppositeOfOutDirection() {
        VisibleCell[][] grid = filledGrid(7, TerritoryView.SELF);
        grid[3][3] = new VisibleCell(new GridPosition(3, 3), OccupantView.EMPTY, TerritoryView.UNOWNED);
        grid[3][4] = new VisibleCell(new GridPosition(4, 3), OccupantView.EMPTY, TerritoryView.UNOWNED);
        StubGameApi game = new StubGameApi(new GridPosition(2, 3), new GridPosition(0, 3), grid);
        EnemyStateMachine controller = new EnemyStateMachine();

        controller.takeTurn(game);
        assertEquals(Direction.EAST, game.movedDirection);

        game.position = new GridPosition(3, 3);
        game.activeTrail = List.of(new GridPosition(2, 3));
        controller.takeTurn(game);
        assertEquals(Direction.EAST, game.movedDirection);

        grid[2][2] = new VisibleCell(new GridPosition(2, 2), OccupantView.EMPTY, TerritoryView.UNOWNED);
        grid[4][3] = new VisibleCell(new GridPosition(3, 4), OccupantView.EMPTY, TerritoryView.UNOWNED);
        grid[4][4] = new VisibleCell(new GridPosition(4, 4), OccupantView.EMPTY, TerritoryView.UNOWNED);
        game.position = new GridPosition(4, 3);
        game.activeTrail = List.of(new GridPosition(2, 3), new GridPosition(3, 3));
        controller.takeTurn(game);
        assertEquals("ACROSS", controller.getDebugState());
        assertEquals(Direction.SOUTH, game.movedDirection);

        grid[5][2] = new VisibleCell(new GridPosition(2, 5), OccupantView.EMPTY, TerritoryView.UNOWNED);
        game.position = new GridPosition(4, 4);
        game.activeTrail = List.of(new GridPosition(2, 3), new GridPosition(3, 3), new GridPosition(4, 3), new GridPosition(4, 4));
        controller.takeTurn(game);

        assertEquals("BACK", controller.getDebugState());
        assertEquals(Direction.WEST, game.movedDirection);
    }

    @Test
    void blockedTerritoryEdgeRepositionsInsteadOfStartingOut() {
        VisibleCell[][] grid = filledGrid(TerritoryView.SELF);
        grid[2][3] = new VisibleCell(
                new GridPosition(3, 2), OccupantView.OPPONENT_TRAIL, TerritoryView.OPPONENT);
        StubGameApi game = new StubGameApi(new GridPosition(2, 2), new GridPosition(0, 2), grid);
        EnemyStateMachine controller = new EnemyStateMachine();

        controller.takeTurn(game);

        assertEquals("REPOSITION", controller.getDebugState());
        assertTrue(List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST).contains(game.movedDirection));
    }

    @Test
    void playsManyTurnsAgainstItselfWithoutFrameworkErrors() throws InterruptedException {
        GameConfig config = new GameConfig(
                20, 20, 11, 40,
                List.of(new GridPosition(4, 10), new GridPosition(15, 10)),
                3, 0, // no auto-play delay in tests
                20, List.of(1L, 2L)
        );
        List<AgentController> controllers = List.of(
                new EnemyStateMachine(),
                new EnemyStateMachine()
        );
        GameEngine engine = new GameEngine(config, controllers);
        BlockingQueue<GameSnapshot> snapshots = new LinkedBlockingQueue<>();
        engine.addObserver(snapshots::add);
        engine.reset(controllers);
        assertNotNull(snapshots.poll(2, TimeUnit.SECONDS));

        engine.start();

        GameSnapshot last = null;
        for (int i = 0; i < 80; i++) { // 40 turns per player, 2 players
            GameSnapshot snapshot = snapshots.poll(2, TimeUnit.SECONDS);
            assertNotNull(snapshot, "engine stalled or threw before completing all turns");
            last = snapshot;
        }

        assertTrue(last.gameOver());
    }

    private static final class StubGameApi implements GameApi {
        private GridPosition position;
        private final GridPosition respawn;
        private final VisibleCell[][] visibleGrid;
        private List<GridPosition> activeTrail = List.of();
        private Direction movedDirection;

        private StubGameApi(GridPosition position, GridPosition respawn, VisibleCell[][] visibleGrid) {
            this.position = position;
            this.respawn = respawn;
            this.visibleGrid = visibleGrid;
        }

        @Override public GridPosition getAgentPosition() { return position; }
        @Override public GridPosition getRespawnPosition() { return respawn; }
        @Override public int getOwnedTerritoryCellCount() { return 25; }
        @Override public int getOpponentTerritoryCellCount() { return 0; }
        @Override public int getRemainingTurns() { return 1; }
        @Override public List<GridPosition> getActiveTrail() { return activeTrail; }
        @Override public VisibleCell[][] getVisibleGrid() { return visibleGrid; }
        @Override public int getBoardWidth() { return visibleGrid[0].length; }
        @Override public int getBoardHeight() { return visibleGrid.length; }

        @Override
        public MoveResult move(Direction direction) {
            movedDirection = direction;
            return MoveResult.MOVED;
        }
    }

    private static VisibleCell[][] filledGrid(TerritoryView territory) {
        return filledGrid(5, territory);
    }

    private static VisibleCell[][] filledGrid(int size, TerritoryView territory) {
        VisibleCell[][] grid = new VisibleCell[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                grid[y][x] = new VisibleCell(new GridPosition(x, y), OccupantView.EMPTY, territory);
            }
        }
        return grid;
    }
}
