package territorygame.domain;

/** Opaque identity for a participant. Never a stand-in for "player 1"/"player 2" naming. */
public record PlayerId(int index) {
}
