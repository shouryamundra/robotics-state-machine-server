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
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.List;

/**
 * Top-level Swing viewer for the full authoritative game. Lets the user
 * pick which controller occupies each player slot and drives Start/Pause/
 * Step/Reset. Contains no game-rule logic; every update arrives as an
 * immutable {@link GameSnapshot} and is marshalled onto the EDT here.
 */
public final class GameWindow extends JFrame implements GameObserver {

    private final GameConfig config;
    private final GameEngine gameEngine;
    private final BoardPanel boardPanel;
    private final JComboBox<ControllerOption> player0Combo = new JComboBox<>(toArray());
    private final JComboBox<ControllerOption> player1Combo = new JComboBox<>(toArray());
    private final PlayerCard[] playerCards = {new PlayerCard(0), new PlayerCard(1)};
    private final JLabel matchStatusLabel = new JLabel();
    private final JLabel errorLabel = new JLabel();

    public GameWindow(GameConfig config) {
        super("Territory Capture");
        this.config = config;
        this.boardPanel = new BoardPanel(config.boardWidth(), config.boardHeight());

        player0Combo.setSelectedIndex(0); // Basic State Machine
        player1Combo.setSelectedIndex(1); // Enemy State Machine

        List<AgentController> initialControllers = currentSelections();
        this.gameEngine = new GameEngine(config, initialControllers);
        this.gameEngine.addObserver(this);

        setLayout(new BorderLayout());
        add(buildTopPanel(config.autoPlayTurnDelayMillis()), BorderLayout.NORTH);
        add(wrapBoard(), BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // pack() sizes from preferred sizes, then fitToUsableScreen() caps
        // and centers inside the display area that excludes the menu bar
        // and Dock, so the window never opens under them or at (0, 0).
        pack();
        fitToUsableScreen();
        // macOS often applies the native window position only after the peer
        // exists; re-run once shown so we don't land at the top-left.
        SwingUtilities.invokeLater(this::fitToUsableScreen);

        // Triggers the first observer notification so the board paints
        // before Start/Step is ever clicked; reuses the same controller
        // instances just constructed above rather than creating a second set.
        gameEngine.reset(initialControllers);
    }

    private static ControllerOption[] toArray() {
        return AvailableControllers.ALL.toArray(new ControllerOption[0]);
    }

    private List<AgentController> currentSelections() {
        return List.of(controllerFrom(player0Combo, 0), controllerFrom(player1Combo, 1));
    }

    private AgentController controllerFrom(JComboBox<ControllerOption> combo, int playerIndex) {
        ControllerOption selected = (ControllerOption) combo.getSelectedItem();
        long seed = config.controllerSeeds().get(playerIndex);
        return selected.factory().apply(seed);
    }

    private JPanel buildTopPanel(int initialTurnDelayMillis) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buildControlsPanel(initialTurnDelayMillis), BorderLayout.NORTH);

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
     * Two stacked rows rather than one wide row: player selection on top,
     * actions and speed below. A single row wide enough for both combo
     * boxes, four buttons, and the speed slider can exceed the window width
     * once a look-and-feel's fonts/padding are taken into account, and
     * BoxLayout doesn't wrap — it would just run off the edge. Splitting the
     * content is a fix that holds regardless of exact pixel widths, rather
     * than another guessed constant.
     */
    private JPanel buildControlsPanel(int initialTurnDelayMillis) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        panel.add(buildPlayerSelectionRow());
        panel.add(Box.createVerticalStrut(10));
        panel.add(buildActionsRow(initialTurnDelayMillis));
        return panel;
    }

    private JPanel buildPlayerSelectionRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

        row.add(new JLabel("Player 1:"));
        row.add(Box.createHorizontalStrut(6));
        row.add(player0Combo);
        row.add(Box.createHorizontalStrut(20));
        row.add(new JLabel("Player 2:"));
        row.add(Box.createHorizontalStrut(6));
        row.add(player1Combo);
        row.add(Box.createHorizontalGlue());

        // Picking a different controller swaps it in for that slot from the
        // next turn on, without resetting the match — position, territory,
        // trail, and turns are all left as they are. Reset still rebuilds a
        // fresh match with whatever's currently selected.
        player0Combo.addActionListener(event -> gameEngine.setController(0, controllerFrom(player0Combo, 0)));
        player1Combo.addActionListener(event -> gameEngine.setController(1, controllerFrom(player1Combo, 1)));

        return row;
    }

    private JPanel buildActionsRow(int initialTurnDelayMillis) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

        JButton startButton = new JButton("Start");
        JButton pauseButton = new JButton("Pause");
        JButton stepButton = new JButton("Step");
        JButton resetButton = new JButton("Reset");
        startButton.addActionListener(event -> gameEngine.start());
        pauseButton.addActionListener(event -> gameEngine.pause());
        stepButton.addActionListener(event -> gameEngine.step());
        resetButton.addActionListener(event -> gameEngine.reset(currentSelections()));
        row.add(startButton);
        row.add(Box.createHorizontalStrut(8));
        row.add(pauseButton);
        row.add(Box.createHorizontalStrut(8));
        row.add(stepButton);
        row.add(Box.createHorizontalStrut(8));
        row.add(resetButton);
        row.add(Box.createHorizontalStrut(28));
        row.add(buildSpeedControl(initialTurnDelayMillis));
        row.add(Box.createHorizontalGlue());

        return row;
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

    private JPanel wrapBoard() {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 12));
        holder.add(boardPanel, BorderLayout.CENTER);
        return holder;
    }

    /** Player cards stacked beside the board so the window is landscape, not a tall column. */
    private JPanel buildSidePanel() {
        JPanel cards = new JPanel(new GridLayout(2, 1, 0, 12));
        for (PlayerCard card : playerCards) {
            cards.add(card.panel);
        }
        JPanel side = new JPanel(new BorderLayout());
        side.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
        side.setPreferredSize(new Dimension(340, boardPanel.getPreferredSize().height));
        side.add(cards, BorderLayout.CENTER);
        return side;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));
        matchStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        matchStatusLabel.setFont(matchStatusLabel.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(matchStatusLabel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Caps the packed size to this display's usable area (menu bar and Dock
     * excluded) and centers the window in that rectangle. The board already
     * scales its cell size to whatever space it is given.
     */
    private void fitToUsableScreen() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int usableX = screen.x + insets.left + 8;
        int usableY = screen.y + insets.top + 8;
        int usableW = screen.width - insets.left - insets.right - 16;
        int usableH = screen.height - insets.top - insets.bottom - 16;

        int width = Math.min(getWidth(), usableW);
        int height = Math.min(getHeight(), usableH);
        setBounds(
                usableX + (usableW - width) / 2,
                usableY + (usableH - height) / 2,
                width,
                height);
        setMinimumSize(new Dimension(Math.min(560, width), Math.min(400, height)));
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

    /** One player's status card: stats on top, wrapping state log filling the rest. */
    private static final class PlayerCard {
        private final JPanel panel;
        private final javax.swing.border.TitledBorder border;
        private final JLabel scoreLabel = new JLabel();
        private final JLabel territoryLabel = new JLabel();
        private final JLabel killsLabel = new JLabel();
        private final JLabel deathsLabel = new JLabel();
        private final JLabel turnsLabel = new JLabel();
        private final JTextArea stateArea = new JTextArea();

        PlayerCard(int index) {
            border = BorderFactory.createTitledBorder("Player " + (index + 1));
            border.setTitleFont(border.getTitleFont() != null
                    ? border.getTitleFont().deriveFont(Font.BOLD)
                    : new JLabel().getFont().deriveFont(Font.BOLD));
            panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createCompoundBorder(
                    border, BorderFactory.createEmptyBorder(6, 10, 10, 10)));

            JPanel swatchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            swatchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(13, 13));
            swatch.setBackground(BoardPanel.AGENT_COLORS[index % BoardPanel.AGENT_COLORS.length]);
            scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD));
            swatchRow.add(swatch);
            swatchRow.add(scoreLabel);

            JPanel stats = new JPanel();
            stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
            stats.add(swatchRow);
            stats.add(Box.createVerticalStrut(4));
            for (JLabel label : new JLabel[] {territoryLabel, killsLabel, deathsLabel, turnsLabel}) {
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                stats.add(label);
            }

            Color well = new Color(250, 250, 250);
            stateArea.setLineWrap(true);
            stateArea.setWrapStyleWord(true);
            stateArea.setEditable(false);
            stateArea.setOpaque(true);
            stateArea.setBackground(well);
            stateArea.setRows(8);
            stateArea.setColumns(24);
            stateArea.setFont(stateArea.getFont().deriveFont(12f));
            stateArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            stateArea.getCaret().setVisible(false);

            JScrollPane stateScroll = new JScrollPane(stateArea);
            stateScroll.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
            stateScroll.getViewport().setBackground(well);
            stateScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            stateScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            JPanel stateBlock = new JPanel(new BorderLayout(0, 4));
            JLabel stateHeader = new JLabel("State:");
            stateHeader.setFont(stateHeader.getFont().deriveFont(Font.BOLD));
            stateBlock.add(stateHeader, BorderLayout.NORTH);
            stateBlock.add(stateScroll, BorderLayout.CENTER);

            GridBagConstraints statsConstraints = new GridBagConstraints();
            statsConstraints.gridx = 0;
            statsConstraints.gridy = 0;
            statsConstraints.weightx = 1;
            statsConstraints.fill = GridBagConstraints.HORIZONTAL;
            statsConstraints.anchor = GridBagConstraints.NORTHWEST;
            statsConstraints.insets = new Insets(0, 0, 8, 0);
            panel.add(stats, statsConstraints);

            GridBagConstraints stateConstraints = new GridBagConstraints();
            stateConstraints.gridx = 0;
            stateConstraints.gridy = 1;
            stateConstraints.weightx = 1;
            stateConstraints.weighty = 1;
            stateConstraints.fill = GridBagConstraints.BOTH;
            panel.add(stateBlock, stateConstraints);
        }

        void update(GameSnapshot.PlayerSnapshot player, boolean active) {
            border.setTitle("Player " + (player.id().index() + 1) + (active ? " (active)" : ""));
            panel.repaint();
            scoreLabel.setText("Score: " + player.score());
            territoryLabel.setText("Territory: " + player.territoryCount());
            killsLabel.setText("Kills: " + player.killCount());
            deathsLabel.setText("Deaths: " + player.deathCount());
            turnsLabel.setText("Turns left: " + player.remainingTurns());
            String state = player.debugState();
            stateArea.setText(state == null || state.isBlank() ? "" : state);
            stateArea.setCaretPosition(0);
            stateArea.getCaret().setVisible(false);
        }
    }
}
