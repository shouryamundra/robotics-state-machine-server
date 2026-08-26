package territorygame.engine;

/** Notified after each turn with the latest match state. Never mutates it. */
public interface GameObserver {

    void onGameStateChanged(GameSnapshot snapshot);
}
