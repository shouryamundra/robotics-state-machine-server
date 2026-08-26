package territorygame.engine;

import org.junit.jupiter.api.Test;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.domain.PlayerId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GameSnapshotTest {

    private final PlayerId player0 = new PlayerId(0);

    private GameSnapshot.CellSnapshot[][] cellsWithOneOwnedCell() {
        GameSnapshot.CellSnapshot[][] cells = new GameSnapshot.CellSnapshot[2][2];
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                cells[y][x] = new GameSnapshot.CellSnapshot(null, null);
            }
        }
        cells[0][0] = new GameSnapshot.CellSnapshot(player0, null);
        return cells;
    }

    private GameSnapshot buildSnapshot(GameSnapshot.CellSnapshot[][] cells) {
        return new GameSnapshot(
                2, 2, cells, List.of(), player0, MoveResult.MOVED, 3, false, null);
    }

    @Test
    void mutatingTheOriginalArrayAfterConstructionDoesNotAffectTheSnapshot() {
        GameSnapshot.CellSnapshot[][] cells = cellsWithOneOwnedCell();
        GameSnapshot snapshot = buildSnapshot(cells);

        cells[0][0] = new GameSnapshot.CellSnapshot(null, null);

        assertEquals(player0, snapshot.cells()[0][0].territoryOwner());
    }

    @Test
    void equalSnapshotsWithDifferentCellArrayInstancesAreEqual() {
        GameSnapshot a = buildSnapshot(cellsWithOneOwnedCell());
        GameSnapshot b = buildSnapshot(cellsWithOneOwnedCell());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void snapshotsWithDifferentCellsAreNotEqual() {
        GameSnapshot.CellSnapshot[][] empty = new GameSnapshot.CellSnapshot[2][2];
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                empty[y][x] = new GameSnapshot.CellSnapshot(null, null);
            }
        }
        GameSnapshot a = buildSnapshot(cellsWithOneOwnedCell());
        GameSnapshot b = buildSnapshot(empty);

        assertNotEquals(a, b);
    }

    @Test
    void playerSnapshotScoreCombinesTerritoryAndKillsWithABonus() {
        GameSnapshot.PlayerSnapshot player = new GameSnapshot.PlayerSnapshot(
                player0, new GridPosition(0, 0), 5, 2, 0, 10, List.of());

        assertEquals(25, player.score()); // 5 territory + 2 kills * 10
    }
}
