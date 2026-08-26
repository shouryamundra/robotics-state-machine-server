package territorygame.domain;

import territorygame.api.GridPosition;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Match configuration. Player count is implicit in
 * {@code respawnPositions.size()}. Window sizes are full odd side lengths
 * (e.g. 11 for an 11x11 visibility window), not radii.
 *
 * <p>Values are not hardcoded here; {@link #loadDefault()} reads them from
 * {@code game-config.properties} on the classpath, so board size, visibility,
 * turn count, respawn positions, and starting-territory shape can all be
 * changed by editing that file.
 */
public record GameConfig(
        int boardWidth,
        int boardHeight,
        int visibilityWindowSize,
        int turnsPerPlayer,
        List<GridPosition> respawnPositions,
        int startingTerritorySize,
        int autoPlayTurnDelayMillis
) {
    private static final String DEFAULT_RESOURCE = "/game-config.properties";

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
                requireInt(properties, "autoplay.turnDelayMillis")
        );
    }

    private static int requireInt(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return Integer.parseInt(value.trim());
    }
}
