package territorygame.engine;

import org.junit.jupiter.api.Test;
import territorygame.TestGames;
import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.domain.GameState;
import territorygame.domain.PlayerId;
import territorygame.rules.MoveResolver;
import territorygame.rules.RespawnService;
import territorygame.rules.TerritoryResolver;
import territorygame.visibility.VisibilityService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnManagerTest {

    private final PlayerId player0 = new PlayerId(0);
    private final PlayerId player1 = new PlayerId(1);
    private final TurnManager turnManager = new TurnManager();

    private Map<PlayerId, GameApiImpl> buildApis(GameState state) {
        MoveResolver moveResolver = new MoveResolver(new RespawnService(), new TerritoryResolver());
        VisibilityService visibilityService = new VisibilityService(5);
        Map<PlayerId, GameApiImpl> apis = new HashMap<>();
        apis.put(player0, new GameApiImpl(state, player0, moveResolver, visibilityService));
        apis.put(player1, new GameApiImpl(state, player1, moveResolver, visibilityService));
        return apis;
    }

    /** Always moves the same direction. */
    private static final class AlwaysMoveController implements AgentController {
        private final Direction direction;

        AlwaysMoveController(Direction direction) {
            this.direction = direction;
        }

        @Override
        public void takeTurn(GameApi game) {
            game.move(direction);
        }
    }

    /** Tries an invalid move once, then always moves EAST after that. */
    private static final class RetryOnceThenEastController implements AgentController {
        private boolean triedInvalidMove;
        private int invocationCount;

        @Override
        public void takeTurn(GameApi game) {
            invocationCount++;
            if (!triedInvalidMove) {
                triedInvalidMove = true;
                game.move(Direction.NORTH); // agent starts at y=0, guaranteed invalid
            } else {
                game.move(Direction.EAST);
            }
        }

        int getInvocationCount() {
            return invocationCount;
        }
    }

    @Test
    void activePlayerAlternatesAfterEachTurn() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        Map<PlayerId, AgentController> controllers = Map.of(
                player0, new AlwaysMoveController(Direction.EAST),
                player1, new AlwaysMoveController(Direction.WEST)
        );
        Map<PlayerId, GameApiImpl> apis = buildApis(state);

        assertEquals(player0, state.getActivePlayerId());

        turnManager.executeTurn(state, controllers, apis);
        assertEquals(player1, state.getActivePlayerId());

        turnManager.executeTurn(state, controllers, apis);
        assertEquals(player0, state.getActivePlayerId());
    }

    @Test
    void controllerIsInvokedAgainAfterAnInvalidMoveWithinTheSameTurn() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(0, 0), List.of(new GridPosition(0, 0)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        RetryOnceThenEastController player0Controller = new RetryOnceThenEastController();
        Map<PlayerId, AgentController> controllers = Map.of(
                player0, player0Controller,
                player1, new AlwaysMoveController(Direction.WEST)
        );
        Map<PlayerId, GameApiImpl> apis = buildApis(state);

        turnManager.executeTurn(state, controllers, apis);

        assertTrue(player0Controller.getInvocationCount() >= 2);
        // Only the one successful move (turn 2) decremented the turn count.
        assertEquals(9, state.getRemainingTurns(player0));
    }

    @Test
    void onlyTheActivePlayersRemainingTurnsAreConsumed() {
        GameState state = TestGames.twoPlayerState(
                8, 8,
                new GridPosition(1, 1), List.of(new GridPosition(1, 1)),
                new GridPosition(6, 6), List.of(new GridPosition(6, 6)),
                10
        );
        Map<PlayerId, AgentController> controllers = Map.of(
                player0, new AlwaysMoveController(Direction.EAST),
                player1, new AlwaysMoveController(Direction.WEST)
        );
        Map<PlayerId, GameApiImpl> apis = buildApis(state);

        turnManager.executeTurn(state, controllers, apis);

        assertEquals(9, state.getRemainingTurns(player0));
        assertEquals(10, state.getRemainingTurns(player1));
    }
}
