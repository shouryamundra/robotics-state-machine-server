package territorygame.api;

/**
 * Territory layer of a cell as seen by a viewing player. Independent of
 * whether an agent or trail is also on the cell.
 */
public enum TerritoryView {
    UNOWNED,
    SELF,
    OPPONENT
}
