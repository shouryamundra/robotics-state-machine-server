package territorygame.engine;

import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.api.VisibleCell;
import territorygame.domain.GameState;
import territorygame.domain.PlayerId;
import territorygame.rules.MoveResolver;
import territorygame.visibility.VisibilityService;

/**
 * Player-scoped facade over authoritative state and the move operation.
 * One instance is created per player and reused for the whole match; it
 * never exposes backend domain objects, and every returned collection is a
 * defensive copy so candidate code cannot mutate authoritative state.
 */
public final class GameApiImpl implements GameApi {

    private final GameState state;
    private final PlayerId owner;
    private final MoveResolver moveResolver;
    private final VisibilityService visibilityService;

    private boolean hasMovedThisTurn;
    private MoveResult lastResultThisTurn;

    public GameApiImpl(GameState state, PlayerId owner, MoveResolver moveResolver, VisibilityService visibilityService) {
        this.state = state;
        this.owner = owner;
        this.moveResolver = moveResolver;
        this.visibilityService = visibilityService;
    }

    @Override
    public GridPosition getAgentPosition() {
        return state.getPlayer(owner).getAgent().getPosition();
    }

    @Override
    public GridPosition getRespawnPosition() {
        return state.getPlayer(owner).getAgent().getRespawnPosition();
    }

    @Override
    public int getOwnedTerritoryCellCount() {
        return state.getBoard().territoryCount(owner);
    }

    @Override
    public int getOpponentTerritoryCellCount() {
        return state.getBoard().territoryCount(state.getOpponent(owner).getId());
    }

    @Override
    public int getRemainingTurns() {
        return state.getRemainingTurns(owner);
    }

    @Override
    public java.util.List<GridPosition> getActiveTrail() {
        return state.getPlayer(owner).getAgent().getActiveTrail();
    }

    @Override
    public VisibleCell[][] getVisibleGrid() {
        return visibilityService.computeVisibleGrid(state, owner);
    }

    @Override
    public int getBoardWidth() {
        return state.getBoard().getWidth();
    }

    @Override
    public int getBoardHeight() {
        return state.getBoard().getHeight();
    }

    @Override
    public MoveResult move(Direction direction) {
        if (hasMovedThisTurn) {
            return MoveResult.INVALID;
        }
        MoveResult result = moveResolver.resolve(state, owner, direction);
        if (result != MoveResult.INVALID) {
            hasMovedThisTurn = true;
            lastResultThisTurn = result;
        }
        return result;
    }

    /** Clears the one-successful-move-per-turn guard. Called before each turn. */
    void resetForNewTurn() {
        hasMovedThisTurn = false;
        lastResultThisTurn = null;
    }

    boolean wasMoveMadeThisTurn() {
        return hasMovedThisTurn;
    }

    MoveResult getLastResultThisTurn() {
        return lastResultThisTurn;
    }
}
