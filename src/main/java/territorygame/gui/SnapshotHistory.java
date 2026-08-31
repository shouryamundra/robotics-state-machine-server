package territorygame.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Cursor over snapshots the GUI has already received. Recording always
 * appends and jumps to live; back/forward only change which snapshot is
 * shown, never match state.
 */
final class SnapshotHistory<T> {

    private final List<T> snapshots = new ArrayList<>();
    private int index = -1;

    void record(T snapshot) {
        snapshots.add(snapshot);
        index = snapshots.size() - 1;
    }

    void clear() {
        snapshots.clear();
        index = -1;
    }

    boolean back() {
        return back(1);
    }

    boolean back(int steps) {
        boolean moved = false;
        for (int i = 0; i < steps; i++) {
            if (index <= 0) {
                return moved;
            }
            index--;
            moved = true;
        }
        return moved;
    }

    boolean forward() {
        return forward(1);
    }

    boolean forward(int steps) {
        boolean moved = false;
        for (int i = 0; i < steps; i++) {
            if (index >= snapshots.size() - 1) {
                return moved;
            }
            index++;
            moved = true;
        }
        return moved;
    }

    T current() {
        return snapshots.get(index);
    }

    boolean canGoBack() {
        return index > 0;
    }

    boolean canGoForward() {
        return index >= 0 && index < snapshots.size() - 1;
    }

    boolean isAtLive() {
        return index == snapshots.size() - 1;
    }

    int position() {
        return index + 1;
    }

    int size() {
        return snapshots.size();
    }
}
