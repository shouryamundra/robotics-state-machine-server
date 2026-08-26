package territorygame.domain;

import territorygame.api.GridPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * The moving piece a player controls: its position, configured respawn
 * point, and ordered active trail. Holds no decision-making strategy.
 *
 * <p>Mutators are public for use by the {@code rules}/{@code engine} layers,
 * which live in separate packages. The candidate-facing boundary is
 * preserved not by Java visibility but by never handing an {@code Agent}
 * reference to candidate code or the GUI.
 */
public final class Agent {

    private final GridPosition respawnPosition;
    private GridPosition position;
    private final List<GridPosition> activeTrail = new ArrayList<>();

    public Agent(GridPosition position, GridPosition respawnPosition) {
        this.position = position;
        this.respawnPosition = respawnPosition;
    }

    public GridPosition getPosition() {
        return position;
    }

    public void setPosition(GridPosition position) {
        this.position = position;
    }

    public GridPosition getRespawnPosition() {
        return respawnPosition;
    }

    public List<GridPosition> getActiveTrail() {
        return List.copyOf(activeTrail);
    }

    public boolean isTrailEmpty() {
        return activeTrail.isEmpty();
    }

    public void appendTrail(GridPosition cell) {
        activeTrail.add(cell);
    }

    public void clearTrail() {
        activeTrail.clear();
    }
}
