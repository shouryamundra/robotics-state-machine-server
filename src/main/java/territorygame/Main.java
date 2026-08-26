package territorygame;

import territorygame.domain.GameConfig;
import territorygame.gui.GameWindow;

import javax.swing.SwingUtilities;

/** Application entry point: launches the Swing viewer on the EDT. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameWindow(GameConfig.loadDefault()).setVisible(true));
    }
}
