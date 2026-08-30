package territorygame.api;

/**
 * Head/trail layer of a cell as seen by a viewing player. Agent wins over
 * trail on this layer only; territory is reported separately.
 */
public enum OccupantView {
    EMPTY,
    SELF_TRAIL,
    OPPONENT_TRAIL,
    SELF_AGENT,
    OPPONENT_AGENT
}
