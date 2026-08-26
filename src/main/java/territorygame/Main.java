package territorygame;

import com.formdev.flatlaf.FlatLightLaf;
import territorygame.domain.GameConfig;
import territorygame.gui.GameWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Application entry point: launches the Swing viewer on the EDT. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            useFlatLightLookAndFeel();
            new GameWindow(GameConfig.loadDefault()).setVisible(true);
        });
    }

    /**
     * Swing defaults to the plain cross-platform "Metal" theme unless told
     * otherwise, which is what makes a Swing app look dated. FlatLaf is a
     * pure look-and-feel (still plain Swing widgets underneath, no
     * alternate GUI toolkit) that gives a modern, flat appearance.
     */
    private static void useFlatLightLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            // Fall back to the default theme; a look-and-feel failure isn't worth crashing over.
        }
    }
}
