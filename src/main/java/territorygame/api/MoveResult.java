package territorygame.api;

/** Outcome of a single {@link GameApi#move(Direction)} call. */
public enum MoveResult {
    MOVED,
    CAPTURED,
    DIED,
    INVALID
}
