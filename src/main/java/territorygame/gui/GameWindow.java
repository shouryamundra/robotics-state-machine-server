package territorygame.gui;

import territorygame.api.AgentController;
import territorygame.controller.AvailableControllers;
import territorygame.controller.AvailableControllers.ControllerOption;
import territorygame.domain.GameConfig;
import territorygame.engine.GameEngine;
import territorygame.engine.GameObserver;
import territorygame.engine.GameSnapshot;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/**
 * Top-level Swing viewer for the full authoritative game. Lets the user
 * pick which controller occupies each player slot and drives Start/Pause/
 * Step/Reset. Contains no game-rule logic; every update arrives as an
 * immutable {@link GameSnapshot} and is marshalled onto the EDT here.
 */
public final class GameWindow extends JFrame implements GameObserver {

    private final GameEngine gameEngine;
    private final BoardPanel boardPanel = new BoardPanel();
    private final JComboBox<ControllerOption> player0Combo = new JComboBox<>(toArray());
    private final JComboBox<ControllerOption> player1Combo = new JComboBox<>(toArray());
    private final PlayerCard[] playerCards = {new PlayerCard(0), new PlayerCard(1)};
    private final JLabel matchStatusLabel = new JLabel();
    private final JLabel errorLabel = new JLabel();

    public GameWindow(GameConfig config) {
        super("Territory Capture");

        player0Combo.setSelectedIndex(0); // Basic State Machine
        player1Combo.setSelectedIndex(1); // Enemy State Machine

        List<AgentController> initialControllers = currentSelections();
        this.gameEngine = new GameEngine(config, initialControllers);
        this.gameEngine.addObserver(this);

        setLayout(new BorderLayout());
        add(buildTopPanel(config.autoPlayTurnDelayMillis()), BorderLayout.NORTH);
        add(boardPanel, BorderLayout.CENTER);
        add(buildStatusPanel(), BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 980);
        setLocationRelativeTo(null);

        // Triggers the first observer notification so the board paints
        // before Start/Step is ever clicked; reuses the same controller
        // instances just constructed above rather than creating a second set.
        gameEngine.reset(initialControllers);
    }

    private static ControllerOption[] toArray() {
        return AvailableControllers.ALL.toArray(new ControllerOption[0]);
    }

    private List<AgentController> currentSelections() {
        return List.of(controllerFrom(player0Combo), controllerFrom(player1Combo));
    }

    private AgentController controllerFrom(JComboBox<ControllerOption> combo) {
        ControllerOption selected = (ControllerOption) combo.getSelectedItem();
        return selected.factory().get();
    }

    private JPanel buildTopPanel(int initialTurnDelayMillis) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buildControlsRow(initialTurnDelayMillis), BorderLayout.NORTH);

        errorLabel.setForeground(new Color(150, 0, 0));
        errorLabel.setOpaque(true);
        errorLabel.setBackground(new Color(255, 225, 225));
        errorLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        errorLabel.setVisible(false);
        panel.add(errorLabel, BorderLayout.SOUTH);

        return panel;
    }

    private static final int MIN_TURN_DELAY_MILLIS = 0;
    private static final int MAX_TURN_DELAY_MILLIS = 500;

    /**
     * BoxLayout (not FlowLayout) is deliberate: FlowLayout wraps overflow to
     * a second line when the row is a bit too wide for the container, and
     * that second line doesn't reliably get laid out with the rest on the
     * first paint — a button can end up present but invisible. BoxLayout
     * keeps everything in one row and shrinks instead of wrapping.
     */
    private JPanel buildControlsRow(int initialTurnDelayMillis) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        panel.add(new JLabel("Player 1:"));
        panel.add(Box.createHorizontalStrut(6));
        panel.add(player0Combo);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(new JLabel("Player 2:"));
        panel.add(Box.createHorizontalStrut(6));
        panel.add(player1Combo);
        panel.add(Box.createHorizontalStrut(28));

        JButton startButton = new JButton("Start");
        JButton pauseButton = new JButton("Pause");
        JButton stepButton = new JButton("Step");
        JButton resetButton = new JButton("Reset");
        startButton.addActionListener(event -> gameEngine.start());
        pauseButton.addActionListener(event -> gameEngine.pause());
        stepButton.addActionListener(event -> gameEngine.step());
        resetButton.addActionListener(event -> gameEngine.reset(currentSelections()));
        panel.add(startButton);
        panel.add(Box.createHorizontalStrut(8));
        panel.add(pauseButton);
        panel.add(Box.createHorizontalStrut(8));
        panel.add(stepButton);
        panel.add(Box.createHorizontalStrut(8));
        panel.add(resetButton);
        panel.add(Box.createHorizontalStrut(28));
        panel.add(buildSpeedControl(initialTurnDelayMillis));
        panel.add(Box.createHorizontalGlue());

        // Picking a different controller swaps it in for that slot from the
        // next turn on, without resetting the match — position, territory,
        // trail, and turns are all left as they are. Reset still rebuilds a
        // fresh match with whatever's currently selected.
        player0Combo.addActionListener(event -> gameEngine.setController(0, controllerFrom(player0Combo)));
        player1Combo.addActionListener(event -> gameEngine.setController(1, controllerFrom(player1Combo)));

        return panel;
    }

    /** Controls the pause between turns during continuous play (Start); Step always runs immediately. */
    private JPanel buildSpeedControl(int initialTurnDelayMillis) {
        int clampedInitial = Math.max(MIN_TURN_DELAY_MILLIS, Math.min(MAX_TURN_DELAY_MILLIS, initialTurnDelayMillis));

        JSlider speedSlider = new JSlider(MIN_TURN_DELAY_MILLIS, MAX_TURN_DELAY_MILLIS, clampedInitial);
        speedSlider.setPreferredSize(new Dimension(120, speedSlider.getPreferredSize().height));
        // Slider value is the turn delay in milliseconds: left (0) is fastest, right is slowest.
        speedSlider.addChangeListener(event -> gameEngine.setTurnDelayMillis(speedSlider.getValue()));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(new JLabel("Speed:"));
        panel.add(Box.createHorizontalStrut(4));
        panel.add(new JLabel("Fast"));
        panel.add(speedSlider);
        panel.add(new JLabel("Slow"));
        return panel;
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 16, 16, 16));

        JPanel cards = new JPanel(new GridLayout(1, 2, 20, 0));
        for (PlayerCard card : playerCards) {
            cards.add(card.panel);
        }
        panel.add(cards, BorderLayout.CENTER);

        matchStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        matchStatusLabel.setFont(matchStatusLabel.getFont().deriveFont(Font.BOLD, 13f));
        matchStatusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        panel.add(matchStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    @Override
    public void onGameStateChanged(GameSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            boardPanel.setSnapshot(snapshot);
            updateStatus(snapshot);
        });
    }

    private void updateStatus(GameSnapshot snapshot) {
        List<GameSnapshot.PlayerSnapshot> players = snapshot.players();
        for (int i = 0; i < players.size() && i < playerCards.length; i++) {
            playerCards[i].update(players.get(i), players.get(i).id().equals(snapshot.activePlayerId()));
        }

        String statusText = snapshot.gameOver()
                ? "Game over — " + winnerText(players)
                : "Active: Player " + (indexOfActivePlayer(snapshot) + 1)
                    + "   ·   Last move: " + (snapshot.lastMoveResult() == null ? "-" : snapshot.lastMoveResult());
        matchStatusLabel.setText(statusText);

        boolean hasError = snapshot.errorMessage() != null;
        errorLabel.setText(hasError ? snapshot.errorMessage() : "");
        errorLabel.setVisible(hasError);
    }

    private int indexOfActivePlayer(GameSnapshot snapshot) {
        List<GameSnapshot.PlayerSnapshot> players = snapshot.players();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(snapshot.activePlayerId())) {
                return i;
            }
        }
        return -1;
    }

    private String winnerText(List<GameSnapshot.PlayerSnapshot> players) {
        GameSnapshot.PlayerSnapshot a = players.get(0);
        GameSnapshot.PlayerSnapshot b = players.get(1);
        if (a.territoryCount() == b.territoryCount()) {
            return "draw";
        }
        int winnerIndex = a.territoryCount() > b.territoryCount() ? 0 : 1;
        return "Player " + (winnerIndex + 1) + " wins";
    }

    /** One player's status card: colored swatch plus score/territory/kills/turns. */
    private static final class PlayerCard {
        private final JPanel panel;
        private final javax.swing.border.TitledBorder border;
        private final JLabel scoreLabel = new JLabel();
        private final JLabel territoryLabel = new JLabel();
        private final JLabel killsLabel = new JLabel();
        private final JLabel deathsLabel = new JLabel();
        private final JLabel turnsLabel = new JLabel();

        PlayerCard(int index) {
            border = BorderFactory.createTitledBorder("Player " + (index + 1));
            border.setTitleFont(border.getTitleFont() != null
                    ? border.getTitleFont().deriveFont(Font.BOLD)
                    : new JLabel().getFont().deriveFont(Font.BOLD));
            panel = new JPanel(new GridLayout(5, 1, 0, 4));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    border, BorderFactory.createEmptyBorder(6, 10, 10, 10)));

            JPanel swatchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(13, 13));
            swatch.setBackground(BoardPanel.AGENT_COLORS[index % BoardPanel.AGENT_COLORS.length]);
            scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD));
            swatchRow.add(swatch);
            swatchRow.add(scoreLabel);

            panel.add(swatchRow);
            panel.add(territoryLabel);
            panel.add(killsLabel);
            panel.add(deathsLabel);
            panel.add(turnsLabel);
        }

        void update(GameSnapshot.PlayerSnapshot player, boolean active) {
            border.setTitle("Player " + (player.id().index() + 1) + (active ? " (active)" : ""));
            panel.repaint();
            scoreLabel.setText("Score: " + player.score());
            territoryLabel.setText("Territory: " + player.territoryCount());
            killsLabel.setText("Kills: " + player.killCount());
            deathsLabel.setText("Deaths: " + player.deathCount());
            turnsLabel.setText("Turns left: " + player.remainingTurns());
        }
    }
}
