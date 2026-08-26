package territorygame.engine;

import territorygame.api.AgentController;
import territorygame.api.GridPosition;
import territorygame.domain.Agent;
import territorygame.domain.Board;
import territorygame.domain.GameConfig;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;
import territorygame.rules.MoveResolver;
import territorygame.rules.RespawnService;
import territorygame.rules.TerritoryResolver;
import territorygame.visibility.VisibilityService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinates a match: lifecycle, turn order, controller invocation, the
 * end condition, and observer notification. Implements no capture logic,
 * visibility generation, or rendering.
 *
 * <p>The game is turn-based and single-player-active at a time, so all
 * state mutation is confined to one background thread via a single-thread
 * {@link ExecutorService} — every command (start/step/reset) is simply a
 * task submitted to it, which the executor already guarantees run one at a
 * time in submission order. This keeps game execution off the Swing EDT
 * without any hand-written locking. Call {@link #reset()} once after
 * registering observers to trigger the first render.
 */
public final class GameEngine {

    private final GameConfig config;
    private List<AgentController> controllersInPlayerOrder;
    private final TurnManager turnManager;
    private final List<GameObserver> observers = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(this::newDaemonThread);

    private GameState state;
    private Map<PlayerId, AgentController> controllers;
    private Map<PlayerId, GameApiImpl> apis;

    /**
     * Whether the run loop should keep executing turns. Read by the
     * executor thread on each loop iteration and written by start()/pause()
     * on the caller's thread (the EDT); volatile gives the required
     * cross-thread visibility without a lock.
     */
    private volatile boolean running;

    /**
     * Set when an unexpected exception escapes turn execution itself (as
     * opposed to a controller's own exception, which {@link TurnManager}
     * already contains). Blocks start()/step() until the next reset(); a
     * message reaches the GUI via {@link GameSnapshot#errorMessage()}.
     */
    private volatile boolean halted;
    private volatile String haltMessage;

    /** Pause between turns during continuous play; starts at the configured default, adjustable live from the GUI. */
    private volatile int turnDelayMillis;

    public GameEngine(GameConfig config, List<AgentController> controllersInPlayerOrder) {
        this.config = config;
        this.turnManager = new TurnManager(config.maxAttemptsPerTurn());
        this.turnDelayMillis = config.autoPlayTurnDelayMillis();
        this.controllersInPlayerOrder = List.copyOf(controllersInPlayerOrder);
        buildFreshMatch();
    }

    public void addObserver(GameObserver observer) {
        observers.add(observer);
    }

    /** Changes the pause between turns during continuous play (see start()). Takes effect from the next turn on. */
    public void setTurnDelayMillis(int turnDelayMillis) {
        this.turnDelayMillis = turnDelayMillis;
    }

    /**
     * Replaces the controller for one player slot (0-based, matching the
     * order passed to the constructor/reset) without resetting the match —
     * position, territory, trail, and turns are all left as they are. Takes
     * effect from that player's next turn on.
     */
    public void setController(int playerIndex, AgentController controller) {
        submitSafely(() -> {
            List<AgentController> updated = new ArrayList<>(controllersInPlayerOrder);
            updated.set(playerIndex, controller);
            controllersInPlayerOrder = List.copyOf(updated);

            PlayerId id = state.getPlayers().get(playerIndex).getId();
            controllers.put(id, controller);
        });
    }

    /** Runs turns continuously until paused or the match ends. No-op if already running or halted. */
    public void start() {
        if (running || halted) {
            return;
        }
        running = true;
        submitSafely(this::runUntilPausedOrOver);
    }

    /** Stops the run loop after the in-flight turn finishes. */
    public void pause() {
        running = false;
    }

    /** Runs exactly one turn, regardless of the running flag. No-op if halted. */
    public void step() {
        submitSafely(() -> {
            if (!halted && !state.isGameOver()) {
                runSingleTurnAndPublish();
            }
        });
    }

    /** Rebuilds a fresh match, keeping the current controllers. */
    public void reset() {
        reset(controllersInPlayerOrder);
    }

    /** Rebuilds a fresh match with a new set of controllers (e.g. after the GUI's slot selection changes). */
    public void reset(List<AgentController> controllersInPlayerOrder) {
        List<AgentController> newControllers = List.copyOf(controllersInPlayerOrder);
        submitSafely(() -> {
            running = false;
            halted = false;
            haltMessage = null;
            this.controllersInPlayerOrder = newControllers;
            buildFreshMatch();
            publish(buildSnapshot());
        });
    }

    private void runUntilPausedOrOver() {
        while (running && !state.isGameOver()) {
            runSingleTurnAndPublish();
            if (running) {
                pauseBetweenTurns();
            }
        }
        running = false;
    }

    /**
     * Submits a task to the background executor, guarding against any
     * exception that isn't already contained by {@link TurnManager} (e.g. a
     * bug in the engine's own plumbing rather than a controller). Without
     * this, such an exception would kill the executor's in-flight task
     * silently and the match would just stop updating with no visible cause.
     */
    private void submitSafely(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                handleFatalError(t);
            }
        });
    }

    private void handleFatalError(Throwable t) {
        running = false;
        halted = true;
        haltMessage = "Internal engine error: " + t;
        t.printStackTrace();
        try {
            publish(buildSnapshot());
        } catch (Throwable ignored) {
            // buildSnapshot()/publish() itself is what's broken; nothing more we can safely do.
        }
    }

    /** Paces continuous play so a match is watchable instead of finishing instantly. Step bypasses this entirely. */
    private void pauseBetweenTurns() {
        try {
            Thread.sleep(turnDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runSingleTurnAndPublish() {
        turnManager.executeTurn(state, controllers, apis);
        if (state.getLastTurnError() != null) {
            System.err.println(state.getLastTurnError());
        }
        publish(buildSnapshot());
    }

    private void buildFreshMatch() {
        Board board = new Board(config.boardWidth(), config.boardHeight());
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < config.respawnPositions().size(); i++) {
            PlayerId id = new PlayerId(i);
            GridPosition respawnPosition = config.respawnPositions().get(i);
            List<GridPosition> startingTerritory = GameConfig.startingTerritoryAround(
                    respawnPosition, config.startingTerritorySize(), config.boardWidth(), config.boardHeight());
            for (GridPosition cell : startingTerritory) {
                board.setTerritoryOwner(cell, id);
            }
            Agent agent = new Agent(respawnPosition, respawnPosition);
            players.add(new Player(id, agent, startingTerritory));
        }

        this.state = new GameState(board, players, config.turnsPerPlayer());

        MoveResolver moveResolver = new MoveResolver(new RespawnService(), new TerritoryResolver());
        VisibilityService visibilityService = new VisibilityService(config.visibilityWindowSize());

        this.controllers = new HashMap<>();
        this.apis = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            PlayerId id = players.get(i).getId();
            controllers.put(id, controllersInPlayerOrder.get(i));
            apis.put(id, new GameApiImpl(state, id, moveResolver, visibilityService));
        }
    }

    private void publish(GameSnapshot snapshot) {
        for (GameObserver observer : observers) {
            observer.onGameStateChanged(snapshot);
        }
    }

    private GameSnapshot buildSnapshot() {
        Board board = state.getBoard();
        int width = board.getWidth();
        int height = board.getHeight();

        GameSnapshot.CellSnapshot[][] cells = new GameSnapshot.CellSnapshot[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridPosition position = new GridPosition(x, y);
                cells[y][x] = new GameSnapshot.CellSnapshot(
                        board.territoryOwnerAt(position),
                        board.trailOwnerAt(position)
                );
            }
        }

        List<GameSnapshot.PlayerSnapshot> playerSnapshots = new ArrayList<>();
        for (Player player : state.getPlayers()) {
            playerSnapshots.add(new GameSnapshot.PlayerSnapshot(
                    player.getId(),
                    player.getAgent().getPosition(),
                    board.territoryCount(player.getId()),
                    state.getKillCount(player.getId()),
                    state.getDeathCount(player.getId()),
                    state.getRemainingTurns(player.getId()),
                    player.getAgent().getActiveTrail(),
                    controllers.get(player.getId()).getDebugState()
            ));
        }

        String errorMessage = halted ? haltMessage : state.getLastTurnError();
        return new GameSnapshot(
                width, height, cells, List.copyOf(playerSnapshots),
                state.getActivePlayerId(), state.getLastMoveResult(),
                config.visibilityWindowSize(), state.isGameOver(), errorMessage
        );
    }

    private Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "game-engine-thread");
        thread.setDaemon(true);
        return thread;
    }
}
