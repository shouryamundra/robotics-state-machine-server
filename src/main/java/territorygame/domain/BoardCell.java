package territorygame.domain;

/**
 * One board cell's ownership state. A cell may have a territory owner and a
 * <em>different</em> player's trail owner at the same time — a trail can
 * cross free space or enemy territory before it closes.
 */
final class BoardCell {

    private PlayerId territoryOwner;
    private PlayerId trailOwner;

    PlayerId getTerritoryOwner() {
        return territoryOwner;
    }

    void setTerritoryOwner(PlayerId owner) {
        this.territoryOwner = owner;
    }

    PlayerId getTrailOwner() {
        return trailOwner;
    }

    void setTrailOwner(PlayerId owner) {
        this.trailOwner = owner;
    }
}
