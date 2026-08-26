package territorygame.domain;

import territorygame.api.GridPosition;

import java.util.List;

/** A match participant: identity, moving agent, and configured starting territory. */
public final class Player {

    private final PlayerId id;
    private final Agent agent;
    private final List<GridPosition> startingTerritory;

    public Player(PlayerId id, Agent agent, List<GridPosition> startingTerritory) {
        this.id = id;
        this.agent = agent;
        this.startingTerritory = List.copyOf(startingTerritory);
    }

    public PlayerId getId() {
        return id;
    }

    public Agent getAgent() {
        return agent;
    }

    public List<GridPosition> getStartingTerritory() {
        return startingTerritory;
    }
}
