package territorygame.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotHistoryTest {

    @Test
    void recordedSnapshotBecomesCurrent() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("initial");
        assertEquals("initial", history.current());
    }

    @Test
    void backShowsThePreviousSnapshot() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("first");
        history.record("second");
        history.back();
        assertEquals("first", history.current());
    }

    @Test
    void backAtTheStartStaysOnTheFirstSnapshot() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("only");
        history.back();
        assertEquals("only", history.current());
    }

    @Test
    void forwardAfterBackReturnsTowardLive() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("first");
        history.record("second");
        history.back();
        history.forward();
        assertEquals("second", history.current());
    }

    @Test
    void recordingWhileReviewingJumpsToTheNewLiveSnapshot() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("first");
        history.record("second");
        history.record("third");
        history.back();
        history.back();
        history.record("fourth");
        assertEquals("fourth", history.current());
    }

    @Test
    void clearDiscardsHistorySoTheNextRecordIsCurrent() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("old");
        history.clear();
        history.record("fresh");
        assertEquals("fresh", history.current());
    }

    @Test
    void canGoBackAndForwardTrackTheCursor() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("first");
        history.record("second");
        assertFalse(history.canGoForward());
        assertTrue(history.canGoBack());
        history.back();
        assertTrue(history.canGoForward());
        assertFalse(history.canGoBack());
    }

    @Test
    void isAtLiveUntilTheUserStepsBack() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("first");
        history.record("second");
        assertTrue(history.isAtLive());
        history.back();
        assertFalse(history.isAtLive());
        history.forward();
        assertTrue(history.isAtLive());
    }

    @Test
    void positionIsOneBasedForStatusText() {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        history.record("first");
        history.record("second");
        history.record("third");
        assertEquals(3, history.position());
        assertEquals(3, history.size());
        history.back();
        assertEquals(2, history.position());
        assertEquals(3, history.size());
    }

    @Test
    void backByTenMovesTenSnapshots() {
        SnapshotHistory<String> history = historyWith(15);
        history.back(10);
        assertEquals("s5", history.current());
    }

    @Test
    void backByTenClampsAtTheStart() {
        SnapshotHistory<String> history = historyWith(4);
        history.back(10);
        assertEquals("s1", history.current());
    }

    @Test
    void forwardByTenMovesTenSnapshots() {
        SnapshotHistory<String> history = historyWith(15);
        history.back(14);
        history.forward(10);
        assertEquals("s11", history.current());
    }

    @Test
    void forwardByTenClampsAtLive() {
        SnapshotHistory<String> history = historyWith(4);
        history.back(2);
        history.forward(10);
        assertEquals("s4", history.current());
    }

    private static SnapshotHistory<String> historyWith(int count) {
        SnapshotHistory<String> history = new SnapshotHistory<>();
        for (int i = 1; i <= count; i++) {
            history.record("s" + i);
        }
        return history;
    }
}
