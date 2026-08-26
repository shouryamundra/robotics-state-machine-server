package territorygame.domain;

import territorygame.api.GridPosition;
import territorygame.helpers.MovementUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Match configuration. Player count is implicit in
 * {@code respawnPositions.size()}. Window sizes are full odd side lengths
 * (e.g. 11 for an 11x11 visibility window), not radii.
 *
 * <p>Values are not hardcoded here; {@link #loadDefault()} reads them from
 * {@code game-config.properties} on the classpath, so board size, visibility,
 * turn count, respawn positions, and starting-territory shape can all be
 * changed by editing that file.
 *
 * <p>Values are validated on construction (both here and via
 * {@link #loadFromClasspath}) so a misconfiguration fails immediately with a
 * clear message instead of surfacing later as a confusing exception deep
 * inside the engine.
 */
public record GameConfig(
        int boardWidth,
        int boardHeight,
        int visibilityWindowSize,
        int turnsPerPlayer,
        List<GridPosition> respawnPositions,
        int startingTerritorySize,
        int autoPlayTurnDelayMillis,
        int maxAttemptsPerTurn
) {
    private static final String DEFAULT_RESOURCE = "/game-config.properties";

    public GameConfig {
        if (boardWidth <= 0 || boardHeight <= 0) {
            throw new IllegalArgumentException(
                    "board.width and board.height must be positive: " + boardWidth + "x" + boardHeight);
        }
        if (visibilityWindowSize <= 0 || visibilityWindowSize % 2 == 0) {
            throw new IllegalArgumentException(
                    "visibility.windowSize must be a positive odd number: " + visibilityWindowSize);
        }
        if (visibilityWindowSize > boardWidth || visibilityWindowSize > boardHeight) {
            throw new IllegalArgumentException(
                    "visibility.windowSize must not exceed the board dimensions: " + visibilityWindowSize);
        }
        if (turnsPerPlayer <= 0) {
            throw new IllegalArgumentException("turns.perPlayer must be positive: " + turnsPerPlayer);
        }
        if (startingTerritorySize <= 0) {
            throw new IllegalArgumentException("starting.territorySize must be positive: " + startingTerritorySize);
        }
        if (autoPlayTurnDelayMillis < 0) {
            throw new IllegalArgumentException(
                    "autoplay.turnDelayMillis must not be negative: " + autoPlayTurnDelayMillis);
        }
        if (maxAttemptsPerTurn <= 0) {
            throw new IllegalArgumentException("turn.maxAttemptsPerTurn must be positive: " + maxAttemptsPerTurn);
        }
        respawnPositions = List.copyOf(respawnPositions);
        for (GridPosition position : respawnPositions) {
            if (!MovementUtils.isWithinBoard(position, boardWidth, boardHeight)) {
                throw new IllegalArgumentException("Respawn position out of bounds: " + position);
            }
        }
        if (startingTerritoriesOverlap(respawnPositions, startingTerritorySize, boardWidth, boardHeight)) {
            throw new IllegalArgumentException("Starting territories overlap for the configured respawn positions");
        }
    }

    public static GameConfig loadDefault() {
        return loadFromClasspath(DEFAULT_RESOURCE);
    }

    public static GameConfig loadFromClasspath(String resourcePath) {
        Properties properties = new Properties();
        try (InputStream in = GameConfig.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Config resource not found: " + resourcePath);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read config resource: " + resourcePath, e);
        }
        return fromProperties(properties);
    }

    private static GameConfig fromProperties(Properties properties) {
        int respawnCount = requireInt(properties, "respawn.count");
        List<GridPosition> respawnPositions = new ArrayList<>(respawnCount);
        for (int i = 0; i < respawnCount; i++) {
            respawnPositions.add(new GridPosition(
                    requireInt(properties, "respawn." + i + ".x"),
                    requireInt(properties, "respawn." + i + ".y")
            ));
        }
        return new GameConfig(
                requireInt(properties, "board.width"),
                requireInt(properties, "board.height"),
                requireInt(properties, "visibility.windowSize"),
                requireInt(properties, "turns.perPlayer"),
                List.copyOf(respawnPositions),
                requireInt(properties, "starting.territorySize"),
                requireInt(properties, "autoplay.turnDelayMillis"),
                requireInt(properties, "turn.maxAttemptsPerTurn")
        );
    }

    private static int requireInt(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return Integer.parseInt(value.trim());
    }

    /** The square of cells (clipped to the board) centered on a respawn position. Shared by validation and match setup. */
    public static List<GridPosition> startingTerritoryAround(GridPosition center, int size, int width, int height) {
        int half = size / 2;
        List<GridPosition> cells = new ArrayList<>();
        for (int y = center.y() - half; y <= center.y() + half; y++) {
            for (int x = center.x() - half; x <= center.x() + half; x++) {
                GridPosition cell = new GridPosition(x, y);
                if (MovementUtils.isWithinBoard(cell, width, height)) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }

    private static boolean startingTerritoriesOverlap(List<GridPosition> respawnPositions, int size, int width, int height) {
        Set<GridPosition> seen = new HashSet<>();
        for (GridPosition respawnPosition : respawnPositions) {
            for (GridPosition cell : startingTerritoryAround(respawnPosition, size, width, height)) {
                if (!seen.add(cell)) {
                    return true;
                }
            }
        }
        return false;
    }
}
