package territorygame.domain;

import org.junit.jupiter.api.Test;
import territorygame.api.GridPosition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the value validation GameConfig performs on construction (the
 * compact constructor runs on every construction path, so a direct call is
 * enough to exercise it without needing a properties resource).
 */
class GameConfigTest {

    private GameConfig validConfigWith(int boardWidth, int boardHeight, int visibilityWindowSize,
                                        int turnsPerPlayer, List<GridPosition> respawnPositions,
                                        int startingTerritorySize, int autoPlayTurnDelayMillis,
                                        int maxAttemptsPerTurn) {
        return new GameConfig(boardWidth, boardHeight, visibilityWindowSize, turnsPerPlayer,
                respawnPositions, startingTerritorySize, autoPlayTurnDelayMillis, maxAttemptsPerTurn,
                List.of(1L, 2L));
    }

    @Test
    void rejectsNonPositiveBoardDimensions() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                0, 10, 5, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, 0, 20));
    }

    @Test
    void rejectsEvenVisibilityWindowSize() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 4, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, 0, 20));
    }

    @Test
    void rejectsVisibilityWindowLargerThanTheBoard() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 11, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, 0, 20));
    }

    @Test
    void rejectsNonPositiveTurnsPerPlayer() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 5, 0, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, 0, 20));
    }

    @Test
    void rejectsNonPositiveStartingTerritorySize() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 5, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 0, 0, 20));
    }

    @Test
    void rejectsNegativeAutoPlayDelay() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 5, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, -1, 20));
    }

    @Test
    void rejectsNonPositiveMaxAttemptsPerTurn() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 5, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, 0, 0));
    }

    @Test
    void rejectsOutOfBoundsRespawnPosition() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 5, 10, List.of(new GridPosition(1, 1), new GridPosition(20, 20)), 1, 0, 20));
    }

    @Test
    void rejectsOverlappingStartingTerritories() {
        assertThrows(IllegalArgumentException.class, () -> validConfigWith(
                10, 10, 5, 10, List.of(new GridPosition(4, 4), new GridPosition(5, 5)), 3, 0, 20));
    }

    @Test
    void rejectsControllerSeedCountMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new GameConfig(
                10, 10, 5, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, 0, 20,
                List.of(1L)));
    }

    @Test
    void acceptsAWellFormedConfig() {
        validConfigWith(10, 10, 5, 10, List.of(new GridPosition(1, 1), new GridPosition(8, 8)), 1, 0, 20);
        // No exception is the assertion.
    }
}
