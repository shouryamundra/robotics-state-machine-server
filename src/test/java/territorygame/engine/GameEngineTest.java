package territorygame.engine;

import org.junit.jupiter.api.Test;
import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.domain.GameConfig;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GameEngine's commands run on its own background thread, so these tests
 * observe results through the GameObserver callback rather than reading
 * state back synchronously.
 */
class GameEngineTest {

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

    private GameSnapshot pollSnapshot(BlockingQueue<GameSnapshot> queue) throws InterruptedException {
        GameSnapshot snapshot = queue.poll(2, TimeUnit.SECONDS);
        assertNotNull(snapshot, "expected an observer notification within 2 seconds");
        return snapshot;
    }

    @Test
    void matchRunsToCompletionWithAlternatingTurnsAndCorrectFinalCounts() throws InterruptedException {
        GameConfig config = new GameConfig(
                8, 8, 5, 2,
                List.of(new GridPosition(1, 1), new GridPosition(6, 6)),
                1, 0, // no auto-play delay in tests
                20, List.of(1L, 2L)
        );
        List<AgentController> controllers = List.of(
                new AlwaysMoveController(Direction.EAST),
                new AlwaysMoveController(Direction.WEST)
        );
        GameEngine engine = new GameEngine(config, controllers);
        BlockingQueue<GameSnapshot> snapshots = new LinkedBlockingQueue<>();
        engine.addObserver(snapshots::add);
        engine.reset(controllers);

        GameSnapshot initial = pollSnapshot(snapshots);
        assertFalse(initial.gameOver());

        engine.start();

        GameSnapshot last = null;
        for (int i = 0; i < 4; i++) { // 2 turns per player, 2 players
            last = pollSnapshot(snapshots);
        }

        assertTrue(last.gameOver());
        for (GameSnapshot.PlayerSnapshot player : last.players()) {
            assertEquals(0, player.remainingTurns());
            assertEquals(1, player.territoryCount()); // neither move returned home to capture
        }
    }

    @Test
    void stepRunsExactlyOneTurn() throws InterruptedException {
        GameConfig config = new GameConfig(
                8, 8, 5, 10,
                List.of(new GridPosition(1, 1), new GridPosition(6, 6)),
                1, 0, // no auto-play delay in tests
                20, List.of(1L, 2L)
        );
        List<AgentController> controllers = List.of(
                new AlwaysMoveController(Direction.EAST),
                new AlwaysMoveController(Direction.WEST)
        );
        GameEngine engine = new GameEngine(config, controllers);
        BlockingQueue<GameSnapshot> snapshots = new LinkedBlockingQueue<>();
        engine.addObserver(snapshots::add);
        engine.reset(controllers);
        pollSnapshot(snapshots); // initial

        engine.step();
        GameSnapshot afterOneStep = pollSnapshot(snapshots);

        // Only player0 (the first active player) should have moved.
        assertEquals(9, afterOneStep.players().get(0).remainingTurns());
        assertEquals(10, afterOneStep.players().get(1).remainingTurns());
        assertEquals(afterOneStep.players().get(1).id(), afterOneStep.activePlayerId());
    }

    @Test
    void resetWithNewControllersReplacesThePreviousOnes() throws InterruptedException {
        GameConfig config = new GameConfig(
                8, 8, 5, 10,
                List.of(new GridPosition(1, 1), new GridPosition(6, 6)),
                1, 0, // no auto-play delay in tests
                20, List.of(1L, 2L)
        );
        List<AgentController> initialControllers = List.of(
                new AlwaysMoveController(Direction.EAST),
                new AlwaysMoveController(Direction.WEST)
        );
        GameEngine engine = new GameEngine(config, initialControllers);
        BlockingQueue<GameSnapshot> snapshots = new LinkedBlockingQueue<>();
        engine.addObserver(snapshots::add);
        engine.reset(initialControllers);
        pollSnapshot(snapshots);

        List<AgentController> newControllers = List.of(
                new AlwaysMoveController(Direction.SOUTH),
                new AlwaysMoveController(Direction.NORTH)
        );
        engine.reset(newControllers);
        GameSnapshot afterReset = pollSnapshot(snapshots);
        assertFalse(afterReset.gameOver());

        engine.step();
        GameSnapshot afterStep = pollSnapshot(snapshots);

        // Player0 started at (1,1); with the new controllers it should have moved
        // SOUTH to (1,2), not EAST to (2,1).
        assertEquals(new GridPosition(1, 2), afterStep.players().get(0).position());
    }
}
