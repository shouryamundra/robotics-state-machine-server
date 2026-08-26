package territorygame.domain;

import territorygame.api.GridPosition;

import java.util.HashSet;
import java.util.Set;

/**
 * Authoritative board-cell state. Agent positions are not tracked here; they
 * live on {@link Agent} and are considered separately by callers doing
 * occupancy checks.
 */
public final class Board {

    private final int width;
    private final int height;
    private final BoardCell[][] cells; // cells[y][x]

    public Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new BoardCell[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x] = new BoardCell();
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isWithinBounds(GridPosition position) {
        return position.x() >= 0 && position.x() < width
                && position.y() >= 0 && position.y() < height;
    }

    public PlayerId territoryOwnerAt(GridPosition position) {
        return cellAt(position).getTerritoryOwner();
    }

    public PlayerId trailOwnerAt(GridPosition position) {
        return cellAt(position).getTrailOwner();
    }

    public void setTerritoryOwner(GridPosition position, PlayerId owner) {
        cellAt(position).setTerritoryOwner(owner);
    }

    public void setTrailOwner(GridPosition position, PlayerId owner) {
        cellAt(position).setTrailOwner(owner);
    }

    public int territoryCount(PlayerId owner) {
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (owner.equals(cells[y][x].getTerritoryOwner())) {
                    count++;
                }
            }
        }
        return count;
    }

    public Set<GridPosition> territoryOf(PlayerId owner) {
        Set<GridPosition> positions = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (owner.equals(cells[y][x].getTerritoryOwner())) {
                    positions.add(new GridPosition(x, y));
                }
            }
        }
        return positions;
    }

    public void clearAllTerritoryOf(PlayerId owner) {
        for (GridPosition position : territoryOf(owner)) {
            setTerritoryOwner(position, null);
        }
    }

    private BoardCell cellAt(GridPosition position) {
        if (!isWithinBounds(position)) {
            throw new IllegalArgumentException("Position out of bounds: " + position);
        }
        return cells[position.y()][position.x()];
    }
}
