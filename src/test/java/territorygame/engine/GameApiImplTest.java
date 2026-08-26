package territorygame.engine;

import org.junit.jupiter.api.Test;
import territorygame.TestGames;
import territorygame.api.Direction;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.domain.GameState;
import territorygame.domain.PlayerId;
import territorygame.rules.MoveResolver;
import territorygame.rules.RespawnService;
import territorygame.rules.TerritoryResolver;
import territorygame.visibility.VisibilityService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameApiImplTest {

    private final PlayerId player0 = new PlayerId(0);

    private GameApiImpl newApi(GameState state) {
        MoveResolver moveResolver = new MoveResolver(new RespawnService(), new TerritoryResolver());
        VisibilityService visibilityService = new VisibilityService(5);
        return new GameApiImpl(state, player0, moveResolver, visibilityService);
    }

    @Test
    void firstMoveThisTurnResolvesNormally() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        GameApiImpl api = newApi(state);
        api.resetForNewTurn();

        MoveResult result = api.move(Direction.EAST);

        assertEquals(MoveResult.MOVED, result);
    }

    @Test
    void secondSuccessfulMoveInTheSameTurnIsRejected() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        GameApiImpl api = newApi(state);
        api.resetForNewTurn();

        api.move(Direction.EAST);
        MoveResult secondResult = api.move(Direction.EAST);

        assertEquals(MoveResult.INVALID, secondResult);
        // Position reflects only the first move, not two.
        assertEquals(new GridPosition(2, 1), state.getPlayer(player0).getAgent().getPosition());
    }

    @Test
    void resetForNewTurnAllowsAnotherSuccessfulMove() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        GameApiImpl api = newApi(state);
        api.resetForNewTurn();
        api.move(Direction.EAST);

        api.resetForNewTurn();
        MoveResult result = api.move(Direction.EAST);

        assertEquals(MoveResult.MOVED, result);
        assertEquals(new GridPosition(3, 1), state.getPlayer(player0).getAgent().getPosition());
    }

    @Test
    void invalidMoveDoesNotConsumeTheOneMovePerTurnAllowance() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        GameApiImpl api = newApi(state);
        api.resetForNewTurn();

        MoveResult firstResult = api.move(Direction.NORTH); // out of bounds
        MoveResult secondResult = api.move(Direction.EAST); // now a real move

        assertEquals(MoveResult.INVALID, firstResult);
        assertEquals(MoveResult.MOVED, secondResult);
    }
}
