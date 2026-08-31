package territorygame.gui;

import org.junit.jupiter.api.Test;
import territorygame.api.MoveResult;
import territorygame.domain.PlayerId;
import territorygame.engine.GameSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSnapshotIngestTest {

    @Test
    void snapshotWithNoLastMoveReplacesHistory() {
        SnapshotHistory<GameSnapshot> history = new SnapshotHistory<>();
        GameSnapshot afterTurn = snapshot(MoveResult.MOVED);
        GameSnapshot afterReset = snapshot(null);

        GameWindow.ingestEngineSnapshot(history, afterTurn);
        GameWindow.ingestEngineSnapshot(history, afterReset);

        assertEquals(afterReset, history.current());
        assertFalse(history.canGoBack());
    }

    @Test
    void snapshotAfterAMoveAppendsToHistory() {
        SnapshotHistory<GameSnapshot> history = new SnapshotHistory<>();
        GameSnapshot first = snapshot(null);
        GameSnapshot second = snapshot(MoveResult.MOVED);

        GameWindow.ingestEngineSnapshot(history, first);
        GameWindow.ingestEngineSnapshot(history, second);

        assertEquals(second, history.current());
        assertTrue(history.canGoBack());
    }

    private static GameSnapshot snapshot(MoveResult lastMove) {
        PlayerId player0 = new PlayerId(0);
        GameSnapshot.CellSnapshot[][] cells = new GameSnapshot.CellSnapshot[1][1];
        cells[0][0] = new GameSnapshot.CellSnapshot(null, null);
        return new GameSnapshot(1, 1, cells, List.of(), player0, lastMove, 3, false, null);
    }
}
