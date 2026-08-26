package territorygame.api;

/**
 * An absolute board coordinate. (0, 0) is top-left; x increases right,
 * y increases down.
 */
public record GridPosition(int x, int y) {
}
