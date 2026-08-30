# Layered cell view

A visible cell reports a head/trail layer and a territory layer independently, so a trail or agent no longer hides the land underneath.

## Problem

`BoardCell` already stores `territoryOwner` and `trailOwner` as separate fields. The candidate API collapses them into one `CellViewType` with precedence AGENT > TRAIL > TERRITORY > FREE. The GUI does the same: `BoardPanel.colorFor` fills the cell with trail color when a trail is present.

An enemy cutting through land is indistinguishable from a trail on empty space, both to controllers and to a spectator.

## API

Delete `territorygame.api.CellViewType`.

```java
public enum OccupantView {
    EMPTY,
    SELF_TRAIL,
    OPPONENT_TRAIL,
    SELF_AGENT,
    OPPONENT_AGENT
}

public enum TerritoryView {
    UNOWNED,
    SELF,
    OPPONENT
}

public record VisibleCell(
    GridPosition position,
    OccupantView occupant,
    TerritoryView territory
) {}
```

`OccupantView` is the head/trail layer. `TerritoryView` is the ground layer. Both are relative to the viewing player (`SELF` / `OPPONENT`); candidates never see raw `PlayerId`.

`GameApi.getVisibleGrid()` is unchanged in shape: it still returns `VisibleCell[][]`.

## Classification

`VisibilityService` fills the two fields independently from `Board` plus the two agent positions.

**Occupant** (first match wins):

1. Viewer's agent is here → `SELF_AGENT`
2. Opponent's agent is here → `OPPONENT_AGENT`
3. Viewer's trail is here → `SELF_TRAIL`
4. Opponent's trail is here → `OPPONENT_TRAIL`
5. Otherwise → `EMPTY`

**Territory** (independent of occupant):

1. Viewer owns the cell → `SELF`
2. Opponent owns the cell → `OPPONENT`
3. Otherwise → `UNOWNED`

An agent standing on a trail is `SELF_AGENT` / `OPPONENT_AGENT`, not a trail value. Territory is still reported. The trail under an agent's feet is not a third occupant value; self can already recover it from `getActiveTrail()`.

Old `FREE` is the pair `(EMPTY, UNOWNED)`. Callers that meant “empty unowned space” must check both fields. Callers that meant “no agent or trail” check `occupant == EMPTY` only.

## Helpers

`MovementUtils.isValidMove` rejects a destination whose `occupant` is `OPPONENT_AGENT`. It still does not consult trails or territory.

`ObservedBoard` stores the last full `VisibleCell` seen at each position (both layers). `get` returns `Optional<VisibleCell>`. Null in the grid still means “never observed”; a stored cell with `EMPTY` / `UNOWNED` means “seen and vacant.” `hasObserved` / `clear` / `update` keep their current meaning.

## Controllers

Mechanical updates only; no new states or strategies.

- `BasicStateMachine.isSafeMove`: `occupant != SELF_TRAIL`.
- `EnemyStateMachine`: split `nearestVisible(type)` into occupant and territory lookups. `OPPONENT_TERRITORY` / `SELF_TERRITORY` searches become `territory == OPPONENT` / `SELF`, so land under a trail is now found. Trail and agent searches use `occupant`. The “open neighbor” count that currently checks `FREE` becomes `(EMPTY, UNOWNED)`.
- `RandomStateMachine` and `CandidateController` do not read cell types directly; they pick up the `MovementUtils` change automatically.

## GUI

`GameSnapshot.CellSnapshot` already has both owners. `BoardPanel` paints in layers:

1. Territory fill (unowned gray, or the owner's territory color).
2. Trail as a smaller inner square in the trail color, when `trailOwner != null`.
3. Agent circle, as today.
4. Visibility boxes, as today.

No new snapshot fields.

## Docs

- `CANDIDATE_GUIDE.md`: replace the `CellViewType` section with the two enums and the pair-of-layers rule.
- `README.md` package list: `CellViewType` → `OccupantView`, `TerritoryView`.

## Tests

- `VisibilityServiceTest`: assert both fields. Replace “trail wins over territory” with “trail occupant + territory still visible.” Add a case for opponent trail on the viewer's land: `(OPPONENT_TRAIL, SELF)`.
- `ObservedBoardTest` / `MovementUtilsTest`: construct `VisibleCell` with both fields; `ObservedBoard.get` returns the cell, not a single enum.
- Controller tests that mention `CellViewType` update in place.

## Out of scope

- Game rules, `BoardCell`, capture, death, visibility window size.
- Changing `GameSnapshot` or observer APIs.
- Combinatorial occupant values (no `TRAIL_ON_TERRITORY` enum).
- Showing the trail under an agent's current cell on the occupant layer.
