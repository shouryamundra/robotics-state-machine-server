package territorygame.api;

/**
 * Strategy a player supplies to play the game. The framework invokes
 * {@link #takeTurn(GameApi)} repeatedly for the same turn until a
 * successful move is made.
 */
public interface AgentController {

    void takeTurn(GameApi game);

    /**
     * Optional label for whatever internal state this controller considers
     * itself to be in right now (e.g. an enum name), shown next to it in the
     * GUI. Purely for observing a match; has no effect on gameplay. Return
     * {@code null} (the default) if there's nothing worth showing.
     */
    default String getDebugState() {
        return null;
    }
}
