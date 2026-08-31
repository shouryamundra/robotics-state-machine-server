package territorygame.rules;

import territorygame.api.Direction;
import territorygame.api.GridPosition;
import territorygame.api.MoveResult;
import territorygame.domain.Agent;
import territorygame.domain.Board;
import territorygame.domain.GameState;
import territorygame.domain.Player;
import territorygame.domain.PlayerId;
import territorygame.helpers.MovementUtils;

/**
 * Resolves the rules of a single requested move: bounds and occupancy
 * validation, trail collision (self-death, opponent-kill), trail
 * extension, and trail closure/capture. Resolution order is deterministic
 * and mirrors the spec's rules section exactly.
 */
public final class MoveResolver {

    private final RespawnService respawnService;
    private final TerritoryResolver territoryResolver;

    public MoveResolver(RespawnService respawnService, TerritoryResolver territoryResolver) {
        this.respawnService = respawnService;
        this.territoryResolver = territoryResolver;
    }

    public MoveResult resolve(GameState state, PlayerId moverId, Direction direction) {
        Board board = state.getBoard();
        Player mover = state.getPlayer(moverId);
        Player opponent = state.getOpponent(moverId);
        Agent moverAgent = mover.getAgent();

        GridPosition destination = MovementUtils.nextPosition(moverAgent.getPosition(), direction);
        if (!board.isWithinBounds(destination)) {
            return MoveResult.INVALID;
        }
        if (destination.equals(opponent.getAgent().getPosition())) {
            return MoveResult.INVALID;
        }

        PlayerId trailOwnerAtDestination = board.trailOwnerAt(destination);
        if (moverId.equals(trailOwnerAtDestination)) {
            respawnService.respawn(state, moverId);
            state.incrementDeathCount(moverId);
            state.decrementRemainingTurns(moverId);
            return MoveResult.DIED;
        }

        // Move first so occupancy checks see the mover's destination. Capture
        // before a same-tick kill so the flood-fill can claim enclosed land
        // (including the opponent's start); respawn then restores the start.
        moverAgent.setPosition(destination);

        PlayerId territoryOwnerAtDestination = board.territoryOwnerAt(destination);
        boolean captured = moverId.equals(territoryOwnerAtDestination) && !moverAgent.isTrailEmpty();
        if (captured) {
            territoryResolver.applyCapture(state, moverId);
        }

        if (opponent.getId().equals(trailOwnerAtDestination)) {
            respawnService.respawn(state, opponent.getId());
            state.incrementKillCount(moverId);
            state.incrementDeathCount(opponent.getId());
        }

        if (captured) {
            state.decrementRemainingTurns(moverId);
            return MoveResult.CAPTURED;
        }

        if (!moverId.equals(territoryOwnerAtDestination)) {
            board.setTrailOwner(destination, moverId);
            moverAgent.appendTrail(destination);
        }
        state.decrementRemainingTurns(moverId);
        return MoveResult.MOVED;
    }
}
