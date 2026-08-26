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

class RespawnServiceTest {

    private final PlayerId player0 = new PlayerId(0);
    private final PlayerId player1 = new PlayerId(1);
    private final RespawnService respawnService = new RespawnService();

    private static final GridPosition RESPAWN = new GridPosition(5, 5);
    private static final List<GridPosition> STARTING_TERRITORY = List.of(
            new GridPosition(4, 4), new GridPosition(5, 4), new GridPosition(6, 4),
            new GridPosition(4, 5), new GridPosition(5, 5), new GridPosition(6, 5),
            new GridPosition(4, 6), new GridPosition(5, 6), new GridPosition(6, 6)
    );

    private GameState buildState(GridPosition opponentPosition) {
        return TestGames.twoPlayerState(
                20, 20,
                RESPAWN, STARTING_TERRITORY,
                opponentPosition, List.of(opponentPosition),
                10
        );
    }

    @Test
    void deathClearsTerritoryCapturedBeyondStartingTerritory() {
        GameState state = buildState(new GridPosition(15, 15));
        GridPosition capturedElsewhere = new GridPosition(0, 0);
        state.getBoard().setTerritoryOwner(capturedElsewhere, player0);

        respawnService.respawn(state, player0);

        assertNull(state.getBoard().territoryOwnerAt(capturedElsewhere));
    }

    @Test
    void deathRestoresExactlyTheStartingTerritory() {
        GameState state = buildState(new GridPosition(15, 15));

        respawnService.respawn(state, player0);

        for (GridPosition cell : STARTING_TERRITORY) {
            assertEquals(player0, state.getBoard().territoryOwnerAt(cell));
        }
        assertEquals(STARTING_TERRITORY.size(), state.getBoard().territoryCount(player0));
    }

    @Test
    void respawnPlacesAgentAtConfiguredPositionWhenFree() {
        GameState state = buildState(new GridPosition(15, 15));

        respawnService.respawn(state, player0);

        assertEquals(RESPAWN, state.getPlayer(player0).getAgent().getPosition());
    }

    @Test
    void respawnFallsBackToNearestUnoccupiedStartingCellWhenRespawnPositionIsOccupied() {
        // Opponent sits exactly on player0's respawn cell.
        GameState state = buildState(RESPAWN);

        respawnService.respawn(state, player0);

        // Four cells are at Manhattan distance 1 from (5,5): (5,4),(4,5),(6,5),(5,6).
        // Lowest y first breaks the tie: (5,4).
        assertEquals(new GridPosition(5, 4), state.getPlayer(player0).getAgent().getPosition());
    }

    @Test
    void activeTrailIsClearedOnDeath() {
        GameState state = buildState(new GridPosition(15, 15));
        GridPosition trailCell = new GridPosition(10, 10);
        state.getBoard().setTrailOwner(trailCell, player0);
        state.getPlayer(player0).getAgent().appendTrail(trailCell);

        respawnService.respawn(state, player0);

        assertTrue(state.getPlayer(player0).getAgent().getActiveTrail().isEmpty());
        assertNull(state.getBoard().trailOwnerAt(trailCell));
    }

    @Test
    void respawnFallsBackToTheWholeBoardWhenStartingTerritoryIsFullyBlocked() {
        // A single-cell starting territory with the opponent standing on it:
        // the starting-territory fallback has nowhere to go, so this must
        // fall back to the nearest free cell anywhere on the board instead
        // of throwing.
        GridPosition singleCellRespawn = new GridPosition(5, 5);
        GameState state = TestGames.twoPlayerState(
                20, 20,
                singleCellRespawn, List.of(singleCellRespawn),
                singleCellRespawn, List.of(singleCellRespawn),
                10
        );

        respawnService.respawn(state, player0);

        // Four cells are at Manhattan distance 1 from (5,5); lowest y then x breaks the tie.
        assertEquals(new GridPosition(5, 4), state.getPlayer(player0).getAgent().getPosition());
    }

    @Test
    void respawningOnePlayerDoesNotAffectTheOther() {
        GameState state = buildState(new GridPosition(15, 15));

        respawnService.respawn(state, player0);

        assertEquals(new GridPosition(15, 15), state.getPlayer(player1).getAgent().getPosition());
        assertEquals(1, state.getBoard().territoryCount(player1));
    }
}
