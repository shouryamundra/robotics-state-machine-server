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

        // Move the mover onto its destination before respawning a killed
        // opponent, so RespawnService's occupancy check sees where the mover
        // actually ends up rather than where it moved from. Otherwise, if the
        // mover is stepping onto the opponent's own respawn point, the
        // opponent could respawn there and the mover would then move onto the
        // same cell.
        moverAgent.setPosition(destination);

        if (opponent.getId().equals(trailOwnerAtDestination)) {
            respawnService.respawn(state, opponent.getId());
            state.incrementKillCount(moverId);
            state.incrementDeathCount(opponent.getId());
        }

        PlayerId territoryOwnerAtDestination = board.territoryOwnerAt(destination);
        if (moverId.equals(territoryOwnerAtDestination) && !moverAgent.isTrailEmpty()) {
            territoryResolver.applyCapture(state, moverId);
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
