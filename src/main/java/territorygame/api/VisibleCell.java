package territorygame.api;

/** One cell of a {@link GameApi#getVisibleGrid()} window, with its absolute position. */
public record VisibleCell(GridPosition position, OccupantView occupant, TerritoryView territory) {
}
