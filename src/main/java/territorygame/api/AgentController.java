package territorygame.api;

/**
 * Strategy a player supplies to play the game. The framework invokes
 * {@link #takeTurn(GameApi)} repeatedly for the same turn until a
 * successful move is made.
 */
public interface AgentController {

    void takeTurn(GameApi game);
}
