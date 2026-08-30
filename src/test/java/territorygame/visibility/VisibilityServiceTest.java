package territorygame.visibility;

import org.junit.jupiter.api.Test;
import territorygame.TestGames;
import territorygame.api.GridPosition;
import territorygame.api.OccupantView;
import territorygame.api.TerritoryView;
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

        assertEquals(OccupantView.EMPTY, cellAt(fromPlayer0, player0Territory).occupant());
        assertEquals(TerritoryView.SELF, cellAt(fromPlayer0, player0Territory).territory());
        assertEquals(OccupantView.EMPTY, cellAt(fromPlayer0, player1Territory).occupant());
        assertEquals(TerritoryView.OPPONENT, cellAt(fromPlayer0, player1Territory).territory());
        assertEquals(OccupantView.EMPTY, cellAt(fromPlayer1, player0Territory).occupant());
        assertEquals(TerritoryView.OPPONENT, cellAt(fromPlayer1, player0Territory).territory());
        assertEquals(OccupantView.EMPTY, cellAt(fromPlayer1, player1Territory).occupant());
        assertEquals(TerritoryView.SELF, cellAt(fromPlayer1, player1Territory).territory());
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

        assertEquals(OccupantView.SELF_AGENT, cellAt(grid, new GridPosition(5, 5)).occupant());
        assertEquals(OccupantView.OPPONENT_AGENT, cellAt(grid, new GridPosition(6, 5)).occupant());
    }

    @Test
    void trailOccupantDoesNotHideTerritory() {
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(5, 5), List.of(new GridPosition(5, 5)),
                new GridPosition(0, 0), List.of(),
                10
        );
        GridPosition trailOverTerritory = new GridPosition(4, 5);
        state.getBoard().setTerritoryOwner(trailOverTerritory, player0);
        state.getBoard().setTrailOwner(trailOverTerritory, player0);

        VisibilityService service = new VisibilityService(9);
        VisibleCell cell = cellAt(service.computeVisibleGrid(state, player0), trailOverTerritory);

        assertEquals(OccupantView.SELF_TRAIL, cell.occupant());
        assertEquals(TerritoryView.SELF, cell.territory());
    }

    @Test
    void opponentTrailOnViewerLandReportsBothLayers() {
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(5, 5), List.of(new GridPosition(5, 5)),
                new GridPosition(0, 0), List.of(),
                10
        );
        GridPosition cut = new GridPosition(4, 5);
        state.getBoard().setTerritoryOwner(cut, player0);
        state.getBoard().setTrailOwner(cut, player1);

        VisibilityService service = new VisibilityService(9);
        VisibleCell cell = cellAt(service.computeVisibleGrid(state, player0), cut);

        assertEquals(OccupantView.OPPONENT_TRAIL, cell.occupant());
        assertEquals(TerritoryView.SELF, cell.territory());
    }

    @Test
    void unoccupiedUnownedCellIsEmptyAndUnowned() {
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(5, 5), List.of(new GridPosition(5, 5)),
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                10
        );
        VisibilityService service = new VisibilityService(9);
        VisibleCell cell = cellAt(service.computeVisibleGrid(state, player0), new GridPosition(6, 5));

        assertEquals(OccupantView.EMPTY, cell.occupant());
        assertEquals(TerritoryView.UNOWNED, cell.territory());
    }
}
