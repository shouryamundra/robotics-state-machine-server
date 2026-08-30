package territorygame.rules;

import org.junit.jupiter.api.Test;
import territorygame.TestGames;
import territorygame.api.GridPosition;
import territorygame.domain.GameState;
import territorygame.domain.PlayerId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a trail forming the perimeter of a 3x3 block around (2,2)-(4,4),
 * with the center cell (3,3) as the only cell the flood fill should find
 * enclosed, then calls applyCapture directly to check its effect on the board.
 */
class TerritoryResolverTest {

    private final PlayerId capturer = new PlayerId(0);
    private final PlayerId opponent = new PlayerId(1);
    private final TerritoryResolver resolver = new TerritoryResolver();

    private static final List<GridPosition> PERIMETER = List.of(
            new GridPosition(3, 2), new GridPosition(4, 2),
            new GridPosition(4, 3), new GridPosition(4, 4),
            new GridPosition(3, 4), new GridPosition(2, 4), new GridPosition(2, 3)
    );
    private static final GridPosition HOME = new GridPosition(2, 2);
    private static final GridPosition ENCLOSED = new GridPosition(3, 3);

    private GameState buildStateWithPendingTrail() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                HOME, List.of(HOME),
                new GridPosition(7, 7), List.of(new GridPosition(7, 7)),
                10
        );
        for (GridPosition cell : PERIMETER) {
            state.getBoard().setTrailOwner(cell, capturer);
            state.getPlayer(capturer).getAgent().appendTrail(cell);
        }
        return state;
    }

    @Test
    void simpleRectangularCaptureClaimsTrailAndEnclosedCells() {
        GameState state = buildStateWithPendingTrail();

        resolver.applyCapture(state, capturer);

        for (GridPosition cell : PERIMETER) {
            assertEquals(capturer, state.getBoard().territoryOwnerAt(cell));
        }
        assertEquals(capturer, state.getBoard().territoryOwnerAt(ENCLOSED));
        assertEquals(9, state.getBoard().territoryCount(capturer)); // home + 7 perimeter + 1 enclosed
    }

    @Test
    void captureFlipsOpponentTerritoryInsideTheEnclosedRegion() {
        GameState state = buildStateWithPendingTrail();
        state.getBoard().setTerritoryOwner(ENCLOSED, opponent);

        resolver.applyCapture(state, capturer);

        assertEquals(capturer, state.getBoard().territoryOwnerAt(ENCLOSED));
        // Opponent's own starting cell (7,7), untouched by this capture, is unaffected.
        assertEquals(1, state.getBoard().territoryCount(opponent));
    }

    @Test
    void captureDoesNotClaimOpponentStartingTerritory() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                HOME, List.of(HOME),
                ENCLOSED, List.of(ENCLOSED),
                10
        );
        for (GridPosition cell : PERIMETER) {
            state.getBoard().setTrailOwner(cell, capturer);
            state.getPlayer(capturer).getAgent().appendTrail(cell);
        }

        resolver.applyCapture(state, capturer);

        assertEquals(opponent, state.getBoard().territoryOwnerAt(ENCLOSED));
        assertEquals(1, state.getBoard().territoryCount(opponent));
    }

    @Test
    void unrelatedOpponentTrailInsideTheEnclosedRegionIsUntouched() {
        GameState state = buildStateWithPendingTrail();
        state.getBoard().setTrailOwner(ENCLOSED, opponent);

        resolver.applyCapture(state, capturer);

        assertEquals(capturer, state.getBoard().territoryOwnerAt(ENCLOSED));
        assertEquals(opponent, state.getBoard().trailOwnerAt(ENCLOSED));
    }

    @Test
    void captureClearsTheCapturersActiveTrail() {
        GameState state = buildStateWithPendingTrail();

        resolver.applyCapture(state, capturer);

        assertTrue(state.getPlayer(capturer).getAgent().getActiveTrail().isEmpty());
        for (GridPosition cell : PERIMETER) {
            assertNull(state.getBoard().trailOwnerAt(cell));
        }
    }

    @Test
    void cellsOutsideTheLoopAreNotCaptured() {
        GameState state = buildStateWithPendingTrail();
        GridPosition farAway = new GridPosition(0, 0);

        resolver.applyCapture(state, capturer);

        assertNull(state.getBoard().territoryOwnerAt(farAway));
    }
}
