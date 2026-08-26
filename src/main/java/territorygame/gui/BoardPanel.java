package territorygame.gui;

import territorygame.api.GridPosition;
import territorygame.engine.GameSnapshot;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Renders the full authoritative board with a single custom-painted panel
 * (no per-cell components): territory by owner color, trails, agents, and
 * every player's visibility window (always shown, in that player's color —
 * not just the currently active player's, so the boxes don't flicker on and
 * off as the turn alternates). Pure rendering, no game rules.
 */
public final class BoardPanel extends JPanel {

    private static final Color FREE_COLOR = new Color(235, 235, 235);
    private static final Color GRID_LINE_COLOR = new Color(210, 210, 210);

    /** Shared with GameWindow's status panel so player colors match the board. */
    static final Color[] TERRITORY_COLORS = {
            new Color(120, 170, 235),
            new Color(235, 140, 120)
    };
    private static final Color[] TRAIL_COLORS = {
            new Color(60, 110, 190),
            new Color(190, 80, 60)
    };
    static final Color[] AGENT_COLORS = {
            new Color(20, 60, 130),
            new Color(140, 30, 20)
    };

    private GameSnapshot snapshot;

    /** Must be called on the EDT; the caller owns thread marshalling. */
    void setSnapshot(GameSnapshot snapshot) {
        this.snapshot = snapshot;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (snapshot == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cellSize = Math.max(1, Math.min(getWidth() / snapshot.width(), getHeight() / snapshot.height()));

        paintCells(g, cellSize);
        paintAgents(g, cellSize);
        paintVisibilityWindows(g, cellSize);
    }

    private void paintCells(Graphics2D g, int cellSize) {
        for (int y = 0; y < snapshot.height(); y++) {
            for (int x = 0; x < snapshot.width(); x++) {
                GameSnapshot.CellSnapshot cell = snapshot.cells()[y][x];
                g.setColor(colorFor(cell));
                g.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
            }
        }
        g.setColor(GRID_LINE_COLOR);
        for (int x = 0; x <= snapshot.width(); x++) {
            g.drawLine(x * cellSize, 0, x * cellSize, snapshot.height() * cellSize);
        }
        for (int y = 0; y <= snapshot.height(); y++) {
            g.drawLine(0, y * cellSize, snapshot.width() * cellSize, y * cellSize);
        }
    }

    private Color colorFor(GameSnapshot.CellSnapshot cell) {
        if (cell.trailOwner() != null) {
            return TRAIL_COLORS[cell.trailOwner().index() % TRAIL_COLORS.length];
        }
        if (cell.territoryOwner() != null) {
            return TERRITORY_COLORS[cell.territoryOwner().index() % TERRITORY_COLORS.length];
        }
        return FREE_COLOR;
    }

    private void paintAgents(Graphics2D g, int cellSize) {
        for (GameSnapshot.PlayerSnapshot player : snapshot.players()) {
            Color color = AGENT_COLORS[player.id().index() % AGENT_COLORS.length];
            g.setColor(color);
            int margin = Math.max(1, cellSize / 6);
            g.fillOval(
                    player.position().x() * cellSize + margin,
                    player.position().y() * cellSize + margin,
                    cellSize - 2 * margin,
                    cellSize - 2 * margin
            );
        }
    }

    private void paintVisibilityWindows(Graphics2D g, int cellSize) {
        for (GameSnapshot.PlayerSnapshot player : snapshot.players()) {
            g.setColor(AGENT_COLORS[player.id().index() % AGENT_COLORS.length]);
            drawVisibilityBox(g, cellSize, player.position());
        }
    }

    private void drawVisibilityBox(Graphics2D g, int cellSize, GridPosition center) {
        int half = snapshot.visibilityWindowSize() / 2;
        int minX = Math.max(0, center.x() - half);
        int minY = Math.max(0, center.y() - half);
        int maxX = Math.min(snapshot.width() - 1, center.x() + half);
        int maxY = Math.min(snapshot.height() - 1, center.y() + half);

        g.drawRect(
                minX * cellSize, minY * cellSize,
                (maxX - minX + 1) * cellSize, (maxY - minY + 1) * cellSize
        );
    }
}
