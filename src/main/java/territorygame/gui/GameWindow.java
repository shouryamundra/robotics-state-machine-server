package territorygame.gui;

import territorygame.api.AgentController;
import territorygame.controller.AvailableControllers;
import territorygame.controller.AvailableControllers.ControllerOption;
import territorygame.domain.GameConfig;
import territorygame.engine.GameEngine;
import territorygame.engine.GameObserver;
import territorygame.engine.GameSnapshot;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
    private final JLabel[] playerStatusLabels;
    private final JLabel activePlayerLabel = new JLabel();
    private final JLabel lastMoveLabel = new JLabel();

    public GameWindow(GameConfig config) {
        super("Territory Capture");

        player0Combo.setSelectedIndex(0); // Example Agent
        player1Combo.setSelectedIndex(1); // Provided Bot

        List<AgentController> initialControllers = currentSelections();
        this.gameEngine = new GameEngine(config, initialControllers);
        this.gameEngine.addObserver(this);

        playerStatusLabels = new JLabel[]{new JLabel(), new JLabel()};

        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.NORTH);
        add(boardPanel, BorderLayout.CENTER);
        add(buildStatusPanel(), BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 950);
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
        return List.of(
                ((ControllerOption) player0Combo.getSelectedItem()).factory().get(),
                ((ControllerOption) player1Combo.getSelectedItem()).factory().get()
        );
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Player 1:"));
        panel.add(player0Combo);
        panel.add(new JLabel("Player 2:"));
        panel.add(player1Combo);

        JButton startButton = new JButton("Start");
        JButton pauseButton = new JButton("Pause");
        JButton stepButton = new JButton("Step");
        JButton resetButton = new JButton("Reset");

        startButton.addActionListener(event -> gameEngine.start());
        pauseButton.addActionListener(event -> gameEngine.pause());
        stepButton.addActionListener(event -> gameEngine.step());
        resetButton.addActionListener(event -> gameEngine.reset(currentSelections()));

        // Picking a different controller immediately starts a fresh match with
        // it, rather than silently continuing to play with whatever was
        // selected before — Start/Pause/Step alone never re-read the combo boxes.
        player0Combo.addActionListener(event -> gameEngine.reset(currentSelections()));
        player1Combo.addActionListener(event -> gameEngine.reset(currentSelections()));

        panel.add(startButton);
        panel.add(pauseButton);
        panel.add(stepButton);
        panel.add(resetButton);
        return panel;
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4));
        panel.add(playerStatusLabels[0]);
        panel.add(playerStatusLabels[1]);
        panel.add(activePlayerLabel);
        panel.add(lastMoveLabel);
        return panel;
    }

    @Override
    public void onGameStateChanged(GameSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            boardPanel.setSnapshot(snapshot);
            updateStatusLabels(snapshot);
        });
    }

    private void updateStatusLabels(GameSnapshot snapshot) {
        List<GameSnapshot.PlayerSnapshot> players = snapshot.players();
        for (int i = 0; i < players.size() && i < playerStatusLabels.length; i++) {
            GameSnapshot.PlayerSnapshot player = players.get(i);
            playerStatusLabels[i].setText(String.format(
                    "Player %d — territory: %d, turns left: %d",
                    i + 1, player.territoryCount(), player.remainingTurns()
            ));
        }

        int activeIndex = indexOfActivePlayer(snapshot);
        String activeText = snapshot.gameOver()
                ? "Game over — " + winnerText(snapshot)
                : "Active: Player " + (activeIndex + 1);
        activePlayerLabel.setText(activeText);

        lastMoveLabel.setText("Last move: " + (snapshot.lastMoveResult() == null ? "-" : snapshot.lastMoveResult()));
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

    private String winnerText(GameSnapshot snapshot) {
        List<GameSnapshot.PlayerSnapshot> players = snapshot.players();
        GameSnapshot.PlayerSnapshot a = players.get(0);
        GameSnapshot.PlayerSnapshot b = players.get(1);
        if (a.territoryCount() == b.territoryCount()) {
            return "draw";
        }
        int winnerIndex = a.territoryCount() > b.territoryCount() ? 0 : 1;
        return "Player " + (winnerIndex + 1) + " wins";
    }
}
