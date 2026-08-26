package candidate.examples;

import org.junit.jupiter.api.Test;
import territorygame.api.AgentController;
import territorygame.api.GridPosition;
import territorygame.domain.GameConfig;
import territorygame.engine.GameEngine;
import territorygame.engine.GameSnapshot;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confirms the example agent can play many turns against itself without any framework error. */
class BasicStateMachineTest {

    @Test
    void playsManyTurnsAgainstItselfWithoutFrameworkErrors() throws InterruptedException {
        GameConfig config = new GameConfig(
                20, 20, 11, 40,
                List.of(new GridPosition(4, 10), new GridPosition(15, 10)),
                3, 0, // no auto-play delay in tests
                20
        );
        List<AgentController> controllers = List.of(
                new BasicStateMachine(),
                new BasicStateMachine()
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
}
