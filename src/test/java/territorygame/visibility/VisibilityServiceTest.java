package territorygame.visibility;

import org.junit.jupiter.api.Test;
import territorygame.TestGames;
import territorygame.api.CellViewType;
import territorygame.api.GridPosition;
import territorygame.api.VisibleCell;
import territorygame.domain.GameState;
import territorygame.domain.PlayerId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibilityServiceTest {

    private final PlayerId player0 = new PlayerId(0);
    private final PlayerId player1 = new PlayerId(1);

    private VisibleCell cellAt(VisibleCell[][] grid, GridPosition position) {
        for (VisibleCell[] row : grid) {
            for (VisibleCell cell : row) {
                if (cell.position().equals(position)) {
                    return cell;
                }
            }
        }
        throw new AssertionError("Position not in visible grid: " + position);
    }

    @Test
    void windowIsFullSizeWhenFarFromEveryEdge() {
        GameState state = TestGames.twoPlayerState(
                20, 20,
                new GridPosition(10, 10), List.of(new GridPosition(10, 10)),
                new GridPosition(19, 19), List.of(new GridPosition(19, 19)),
                10
        );
        VisibilityService service = new VisibilityService(5); // half=2

        VisibleCell[][] grid = service.computeVisibleGrid(state, player0);

        assertEquals(5, grid.length);
        assertEquals(5, grid[0].length);
        assertEquals(new GridPosition(10, 10), grid[2][2].position());
    }

    @Test
    void windowClipsAtTheBoardCorner() {
        GameState state = TestGames.twoPlayerState(
                20, 20,
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                new GridPosition(19, 19), List.of(new GridPosition(19, 19)),
                10
        );
        VisibilityService service = new VisibilityService(5); // half=2

        VisibleCell[][] grid = service.computeVisibleGrid(state, player0);

        // Window would be x:[-2,2], y:[-2,2]; clipped to x:[0,2], y:[0,2] -> 3x3.
        assertEquals(3, grid.length);
        assertEquals(3, grid[0].length);
        assertEquals(new GridPosition(0, 0), grid[0][0].position());
    }

    @Test
    void ownershipTranslatesToSelfAndOpponentRelativeToViewer() {
        // Territory cells distinct from either agent's current position, so
        // the AGENT precedence rule doesn't mask the TERRITORY type being checked.
        GridPosition player0Territory = new GridPosition(4, 5);
        GridPosition player1Territory = new GridPosition(7, 6);
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(5, 5), List.of(new GridPosition(5, 5), player0Territory),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6), player1Territory),
                10
        );
        VisibilityService service = new VisibilityService(9);

        VisibleCell[][] fromPlayer0 = service.computeVisibleGrid(state, player0);
        VisibleCell[][] fromPlayer1 = service.computeVisibleGrid(state, player1);

        assertEquals(CellViewType.SELF_TERRITORY, cellAt(fromPlayer0, player0Territory).type());
        assertEquals(CellViewType.OPPONENT_TERRITORY, cellAt(fromPlayer0, player1Territory).type());
        // Same cells, viewed by the other player, flip labels.
        assertEquals(CellViewType.OPPONENT_TERRITORY, cellAt(fromPlayer1, player0Territory).type());
        assertEquals(CellViewType.SELF_TERRITORY, cellAt(fromPlayer1, player1Territory).type());
    }

    @Test
    void agentPositionsAreReportedAsSelfOrOpponentAgent() {
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(5, 5), List.of(new GridPosition(5, 5)),
                new GridPosition(6, 5), List.of(new GridPosition(6, 5)),
                10
        );
        VisibilityService service = new VisibilityService(9);

        VisibleCell[][] grid = service.computeVisibleGrid(state, player0);

        assertEquals(CellViewType.SELF_AGENT, cellAt(grid, new GridPosition(5, 5)).type());
        assertEquals(CellViewType.OPPONENT_AGENT, cellAt(grid, new GridPosition(6, 5)).type());
    }

    @Test
    void precedenceIsAgentThenTrailThenTerritoryThenFree() {
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(5, 5), List.of(new GridPosition(5, 5)),
                new GridPosition(0, 0), List.of(),
                10
        );
        // A cell that is simultaneously player0's territory and player0's trail:
        // trail wins.
        GridPosition trailOverTerritory = new GridPosition(4, 5);
        state.getBoard().setTerritoryOwner(trailOverTerritory, player0);
        state.getBoard().setTrailOwner(trailOverTerritory, player0);

        VisibilityService service = new VisibilityService(9);
        VisibleCell[][] grid = service.computeVisibleGrid(state, player0);

        assertEquals(CellViewType.SELF_TRAIL, cellAt(grid, trailOverTerritory).type());
    }

    @Test
    void unoccupiedUnownedCellIsFree() {
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(5, 5), List.of(new GridPosition(5, 5)),
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                10
        );
        VisibilityService service = new VisibilityService(9);

        VisibleCell[][] grid = service.computeVisibleGrid(state, player0);

        assertEquals(CellViewType.FREE, cellAt(grid, new GridPosition(6, 5)).type());
    }
}
