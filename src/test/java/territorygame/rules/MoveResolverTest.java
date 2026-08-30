package territorygame.rules;

import org.junit.jupiter.api.Test;
import territorygame.TestGames;
import territorygame.api.Direction;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.domain.GameState;
import territorygame.domain.PlayerId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveResolverTest {

    private final PlayerId player0 = new PlayerId(0);
    private final PlayerId player1 = new PlayerId(1);
    private final MoveResolver resolver = new MoveResolver(new RespawnService(), new TerritoryResolver());

    @Test
    void validMoveOutsideTerritoryStartsATrailAndConsumesATurn() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        MoveResult result = resolver.resolve(state, player0, Direction.EAST);

        assertEquals(MoveResult.MOVED, result);
        assertEquals(new GridPosition(2, 1), state.getPlayer(player0).getAgent().getPosition());
        assertEquals(List.of(new GridPosition(2, 1)), state.getPlayer(player0).getAgent().getActiveTrail());
        assertEquals(9, state.getRemainingTurns(player0));
    }

    @Test
    void moveOffTheBoardIsInvalid() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        MoveResult result = resolver.resolve(state, player0, Direction.NORTH);

        assertEquals(MoveResult.INVALID, result);
    }

    @Test
    void moveOntoOpponentAgentIsInvalid() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(2, 1), List.of(new GridPosition(2, 1)),
                10
        );

        MoveResult result = resolver.resolve(state, player0, Direction.EAST);

        assertEquals(MoveResult.INVALID, result);
    }

    @Test
    void invalidMoveLeavesPositionAndTrailUnchanged() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        resolver.resolve(state, player0, Direction.NORTH);

        assertEquals(new GridPosition(0, 0), state.getPlayer(player0).getAgent().getPosition());
        assertTrue(state.getPlayer(player0).getAgent().getActiveTrail().isEmpty());
    }

    @Test
    void invalidMoveDoesNotDecrementTurns() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        resolver.resolve(state, player0, Direction.NORTH);

        assertEquals(10, state.getRemainingTurns(player0));
    }

    @Test
    void secondMoveOutsideTerritoryExtendsTheTrail() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        resolver.resolve(state, player0, Direction.EAST);
        resolver.resolve(state, player0, Direction.EAST);

        assertEquals(
                List.of(new GridPosition(2, 1), new GridPosition(3, 1)),
                state.getPlayer(player0).getAgent().getActiveTrail()
        );
    }

    @Test
    void movingWithinOwnTerritoryWithNoActiveTrailIsJustAMove() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1),
                List.of(new GridPosition(1, 1), new GridPosition(2, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        MoveResult result = resolver.resolve(state, player0, Direction.EAST);

        assertEquals(MoveResult.MOVED, result);
        assertTrue(state.getPlayer(player0).getAgent().getActiveTrail().isEmpty());
    }

    @Test
    void returningToOwnTerritoryWithANonEmptyTrailCloses() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(2, 2), List.of(new GridPosition(2, 2)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        resolver.resolve(state, player0, Direction.EAST); // -> (3,2), trail=[(3,2)]
        MoveResult result = resolver.resolve(state, player0, Direction.WEST); // back to (2,2)

        assertEquals(MoveResult.CAPTURED, result);
        assertTrue(state.getPlayer(player0).getAgent().getActiveTrail().isEmpty());
    }

    @Test
    void movingOntoOwnActiveTrailKillsTheMover() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(2, 2), List.of(new GridPosition(2, 2)),
                new GridPosition(7, 7), List.of(new GridPosition(7, 7)),
                10
        );

        resolver.resolve(state, player0, Direction.EAST);  // (3,2) trail
        resolver.resolve(state, player0, Direction.EAST);  // (4,2) trail
        resolver.resolve(state, player0, Direction.SOUTH); // (4,3) trail
        resolver.resolve(state, player0, Direction.WEST);  // (3,3) trail
        MoveResult result = resolver.resolve(state, player0, Direction.NORTH); // -> (3,2), own trail

        assertEquals(MoveResult.DIED, result);
        assertEquals(new GridPosition(2, 2), state.getPlayer(player0).getAgent().getPosition());
        assertTrue(state.getPlayer(player0).getAgent().getActiveTrail().isEmpty());
        assertEquals(1, state.getBoard().territoryCount(player0));
    }

    @Test
    void crossingOpponentTrailKillsThemAndMoverSurvivesAndContinues() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(7, 7), List.of(new GridPosition(7, 7)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );

        // Player1 lays a trail cell at (7,6) then steps away to (7,5).
        resolver.resolve(state, player1, Direction.EAST);  // (6,6)->(7,6) trail
        resolver.resolve(state, player1, Direction.NORTH); // (7,6)->(7,5) trail

        // Player0 steps onto (7,6): opponent's OLD trail cell laid two moves ago,
        // not currently occupied by player1's agent (which is now at (7,5)).
        MoveResult result = resolver.resolve(state, player0, Direction.NORTH); // (7,7)->(7,6)

        assertEquals(MoveResult.MOVED, result);
        assertEquals(new GridPosition(7, 6), state.getPlayer(player0).getAgent().getPosition());
        // Player1 died and respawned back at its configured respawn position.
        assertEquals(new GridPosition(6, 6), state.getPlayer(player1).getAgent().getPosition());
        assertTrue(state.getPlayer(player1).getAgent().getActiveTrail().isEmpty());
        // The cell player0 stepped onto is now player0's own trail, not player1's.
        assertEquals(player0, state.getBoard().trailOwnerAt(new GridPosition(7, 6)));
    }

    @Test
    void crossingOpponentTrailIncrementsTheMoverKillCount() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(7, 7), List.of(new GridPosition(7, 7)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        resolver.resolve(state, player1, Direction.EAST);  // (6,6)->(7,6) trail
        resolver.resolve(state, player1, Direction.NORTH); // (7,6)->(7,5) trail

        resolver.resolve(state, player0, Direction.NORTH); // (7,7)->(7,6), crosses player1's trail

        assertEquals(1, state.getKillCount(player0));
        assertEquals(0, state.getKillCount(player1));
    }

    @Test
    void movingOntoOpponentsTrailAtItsOwnRespawnPointDoesNotStackAgents() {
        // Regression test for the bug where killing an opponent by stepping
        // onto a trail cell that happens to sit on the opponent's own
        // respawn point could respawn the opponent onto the mover's
        // destination, stacking both agents on one cell.
        GridPosition opponentRespawn = new GridPosition(5, 5);
        List<GridPosition> opponentStartingTerritory = List.of(
                new GridPosition(4, 4), new GridPosition(5, 4), new GridPosition(6, 4),
                new GridPosition(4, 5), new GridPosition(5, 5), new GridPosition(6, 5),
                new GridPosition(4, 6), new GridPosition(5, 6), new GridPosition(6, 6)
        );
        GameState state = TestGames.twoPlayerState(
                10, 10,
                new GridPosition(4, 8), List.of(new GridPosition(4, 8)),
                opponentRespawn, opponentStartingTerritory,
                10
        );
        // Opponent's territory has since shifted away from its respawn
        // point, leaving a trail cell sitting exactly on it.
        state.getPlayer(player1).getAgent().setPosition(new GridPosition(9, 9));
        state.getBoard().setTrailOwner(opponentRespawn, player1);
        state.getPlayer(player1).getAgent().appendTrail(opponentRespawn);
        state.getPlayer(player0).getAgent().setPosition(new GridPosition(4, 5));

        MoveResult result = resolver.resolve(state, player0, Direction.EAST); // (4,5) -> (5,5)

        assertEquals(MoveResult.MOVED, result);
        GridPosition moverPosition = state.getPlayer(player0).getAgent().getPosition();
        GridPosition opponentNewPosition = state.getPlayer(player1).getAgent().getPosition();
        assertEquals(opponentRespawn, moverPosition);
        assertNotEquals(moverPosition, opponentNewPosition);
        assertEquals(1, state.getKillCount(player0));
    }

    @Test
    void closingALoopOnACellThatKillsTheOpponentPreservesTheirStartingTerritory() {
        // Player0 returns home (closing a loop) onto a cell that also has
        // player1's trail, so both a capture and a kill resolve in one move.
        // Player0's existing territory already rings player1's start, so the
        // capture flood-fill would otherwise paint over the start platform
        // that respawn just restored.
        GridPosition p1Start = new GridPosition(4, 4);
        List<GridPosition> p0Territory = List.of(
                new GridPosition(1, 1),
                new GridPosition(3, 3), new GridPosition(4, 3), new GridPosition(5, 3),
                new GridPosition(3, 4),                         new GridPosition(5, 4),
                new GridPosition(3, 5), new GridPosition(4, 5), new GridPosition(5, 5)
        );
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), p0Territory,
                p1Start, List.of(p1Start),
                10
        );

        state.getPlayer(player0).getAgent().setPosition(new GridPosition(1, 2));
        state.getBoard().setTrailOwner(new GridPosition(1, 2), player0);
        state.getPlayer(player0).getAgent().appendTrail(new GridPosition(1, 2));

        state.getPlayer(player1).getAgent().setPosition(new GridPosition(1, 3));
        state.getBoard().setTrailOwner(new GridPosition(1, 1), player1);
        state.getPlayer(player1).getAgent().appendTrail(new GridPosition(1, 1));

        MoveResult result = resolver.resolve(state, player0, Direction.NORTH);

        assertEquals(MoveResult.CAPTURED, result);
        assertEquals(1, state.getKillCount(player0));
        assertEquals(p1Start, state.getPlayer(player1).getAgent().getPosition());
        assertEquals(player1, state.getBoard().territoryOwnerAt(p1Start));
        assertEquals(1, state.getBoard().territoryCount(player1));
    }
}
