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

class SafetyGridDebugTest {

    @Test
    void printTurnByTurnBothSafetyGrid() throws InterruptedException {
        GameConfig config = new GameConfig(
                20, 20, 11, 800,
                List.of(new GridPosition(1, 1), new GridPosition(18, 18)),
                3, 0,
                20, List.of(1L, 2L)
        );
        List<AgentController> controllers = List.of(
                new SafetyGridStateMachine(),
                new SafetyGridStateMachine()
        );
        printMatch(config, controllers, 800);
    }

    @Test
    void printTurnByTurnVsRandom() throws InterruptedException {
        GameConfig config = new GameConfig(
                20, 20, 11, 800,
                List.of(new GridPosition(1, 1), new GridPosition(18, 18)),
                3, 0,
                20, List.of(1L, 2L)
        );
        List<AgentController> controllers = List.of(
                new SafetyGridStateMachine(),
                new RandomStateMachine()
        );
        printMatch(config, controllers, 800);
    }

    private void printMatch(GameConfig config, List<AgentController> controllers, int turnCount) throws InterruptedException {
        GameEngine engine = new GameEngine(config, controllers);
        BlockingQueue<GameSnapshot> snapshots = new LinkedBlockingQueue<>();
        engine.addObserver(snapshots::add);
        engine.reset(controllers);
        snapshots.poll(2, TimeUnit.SECONDS);

        engine.start();

        for (int i = 0; i < turnCount; i++) {
            GameSnapshot snapshot = snapshots.poll(2, TimeUnit.SECONDS);
            if (snapshot == null) {
                System.out.println("stalled at turn " + i);
                break;
            }
            var p0 = snapshot.players().get(0);
            var p1 = snapshot.players().get(1);
            System.out.println("turn " + i + " p0=" + p0.position() + " trailLen=" + p0.trail().size()
                    + " territory=" + p0.territoryCount()
                    + " debug=[" + (p0.debugState() == null ? "" : p0.debugState().replace("\n", "|")) + "]"
                    + " || p1=" + p1.position()
                    + " debug=[" + (p1.debugState() == null ? "" : p1.debugState().replace("\n", "|")) + "]");
        }
    }
}
