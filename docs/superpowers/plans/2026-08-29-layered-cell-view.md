# Layered Cell View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split candidate-visible cells into an occupant layer and a territory layer so a trail or agent no longer hides the land underneath.

**Architecture:** Replace `CellViewType` with `OccupantView` and `TerritoryView`. `VisibleCell` carries both. `VisibilityService` classifies each layer independently from `BoardCell` plus agent positions. Helpers and example controllers switch to the two fields. `BoardPanel` paints territory as the fill and trail as an inset square. Rules and `GameSnapshot` stay unchanged.

**Tech Stack:** Java 21, JUnit 5, Maven (`./mvnw test`), Swing GUI.

## Global Constraints

- Candidate-facing types stay in `territorygame.api`; never leak `PlayerId` to controllers.
- Occupant precedence on that layer only: agent, then trail, then `EMPTY`.
- Territory is independent: `SELF` / `OPPONENT` / `UNOWNED`.
- Old `FREE` is the pair `(EMPTY, UNOWNED)`.
- No rule, capture, death, or snapshot API changes.
- Do not commit unless the user asks.

---

### Task 1: Visibility API and classification

**Files:**
- Create: `src/main/java/territorygame/api/OccupantView.java`
- Create: `src/main/java/territorygame/api/TerritoryView.java`
- Modify: `src/main/java/territorygame/api/VisibleCell.java`
- Modify: `src/main/java/territorygame/visibility/VisibilityService.java`
- Delete: `src/main/java/territorygame/api/CellViewType.java` (after later tasks compile)
- Test: `src/test/java/territorygame/visibility/VisibilityServiceTest.java`

**Interfaces:**
- Consumes: `Board.trailOwnerAt`, `Board.territoryOwnerAt`, agent positions
- Produces: `VisibleCell(GridPosition position, OccupantView occupant, TerritoryView territory)`

- [ ] **Step 1: Write the failing tests**

Replace `CellViewType` assertions with both layers. Rename the precedence test. Add opponent-trail-on-self-land.

```java
assertEquals(OccupantView.EMPTY, cellAt(fromPlayer0, player0Territory).occupant());
assertEquals(TerritoryView.SELF, cellAt(fromPlayer0, player0Territory).territory());
assertEquals(OccupantView.EMPTY, cellAt(fromPlayer0, player1Territory).occupant());
assertEquals(TerritoryView.OPPONENT, cellAt(fromPlayer0, player1Territory).territory());

assertEquals(OccupantView.SELF_AGENT, cellAt(grid, new GridPosition(5, 5)).occupant());
assertEquals(OccupantView.OPPONENT_AGENT, cellAt(grid, new GridPosition(6, 5)).occupant());

// trail occupant + territory still visible
assertEquals(OccupantView.SELF_TRAIL, cell.occupant());
assertEquals(TerritoryView.SELF, cell.territory());

// unoccupied unowned
assertEquals(OccupantView.EMPTY, cell.occupant());
assertEquals(TerritoryView.UNOWNED, cell.territory());

// opponent trail on viewer's land
assertEquals(OccupantView.OPPONENT_TRAIL, cell.occupant());
assertEquals(TerritoryView.SELF, cell.territory());
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q -Dtest=territorygame.visibility.VisibilityServiceTest test`

Expected: compile failure (`OccupantView` / `occupant()` missing).

- [ ] **Step 3: Write minimal implementation**

```java
public enum OccupantView {
    EMPTY, SELF_TRAIL, OPPONENT_TRAIL, SELF_AGENT, OPPONENT_AGENT
}

public enum TerritoryView {
    UNOWNED, SELF, OPPONENT
}

public record VisibleCell(GridPosition position, OccupantView occupant, TerritoryView territory) {}
```

`VisibilityService` fills occupant (agent, then trail, else `EMPTY`) and territory (owner vs viewer, else `UNOWNED`) independently.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q -Dtest=territorygame.visibility.VisibilityServiceTest test`

Expected: PASS. Other modules will still fail to compile until later tasks.

- [ ] **Step 5: Commit**

Skip unless the user asks.

---

### Task 2: Helpers

**Files:**
- Modify: `src/main/java/territorygame/helpers/MovementUtils.java`
- Modify: `src/main/java/territorygame/helpers/ObservedBoard.java`
- Test: `src/test/java/territorygame/helpers/MovementUtilsTest.java`
- Test: `src/test/java/territorygame/helpers/ObservedBoardTest.java`

**Interfaces:**
- Consumes: `VisibleCell.occupant()`, `VisibleCell.territory()`
- Produces: `ObservedBoard.get` → `Optional<VisibleCell>`; `isValidMove` rejects `OccupantView.OPPONENT_AGENT`

- [ ] **Step 1: Write the failing tests**

Construct cells as `new VisibleCell(position, occupant, territory)`.

`ObservedBoard.get` returns the stored `VisibleCell`, not a single enum:

```java
VisibleCell stored = new VisibleCell(position, OccupantView.EMPTY, TerritoryView.SELF);
board.update(new VisibleCell[][]{{stored}});
assertEquals(Optional.of(stored), board.get(position));
```

`isValidMove` still rejects opponent agent via `occupant()`. A free in-bounds cell is `(EMPTY, UNOWNED)`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q -Dtest=territorygame.helpers.ObservedBoardTest,territorygame.helpers.MovementUtilsTest test`

Expected: compile failure on old `VisibleCell` / `CellViewType` constructors.

- [ ] **Step 3: Write minimal implementation**

`MovementUtils.isValidMove`: `cell.occupant() != OccupantView.OPPONENT_AGENT`.

`ObservedBoard` stores `VisibleCell[][]`; `get` returns `Optional.ofNullable(observed[y][x])`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q -Dtest=territorygame.helpers.ObservedBoardTest,territorygame.helpers.MovementUtilsTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Skip unless the user asks.

---

### Task 3: Controllers

**Files:**
- Modify: `src/main/java/candidate/examples/BasicStateMachine.java`
- Modify: `src/main/java/territorygame/controller/EnemyStateMachine.java`

**Interfaces:**
- Consumes: `VisibleCell.occupant()`, `VisibleCell.territory()`
- Produces: same controller behavior, with territory searches seeing land under trails

- [ ] **Step 1: Write the failing test**

No dedicated controller unit tests exist for cell-type reads. Compile the existing suite after the mechanical edits; `BasicStateMachineTest` still covers move/state flow.

- [ ] **Step 2: Confirm the old types no longer compile**

`cell.type()` and `CellViewType` references fail once Task 1 is in. That is the red signal.

- [ ] **Step 3: Write minimal implementation**

`BasicStateMachine.isSafeMove`: `cell.occupant() != OccupantView.SELF_TRAIL`.

`EnemyStateMachine`:
- `nearestOccupant(game, OccupantView)` / `nearestTerritory(game, TerritoryView)`
- Receding “already home”: `territory() == TerritoryView.SELF`
- Safe directions: occupant is not `SELF_TRAIL`, `OPPONENT_TRAIL`, or `OPPONENT_AGENT`
- Huntable directions: occupant is not `SELF_TRAIL` or `OPPONENT_AGENT`
- Open neighbor: `(EMPTY, UNOWNED)`; off-board stays not-open (`null`)
- Unseen on-board cells default to `(EMPTY, UNOWNED)` (old `FREE`)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test`

Expected: compile + tests PASS except any leftover `CellViewType` references (fix those in this task).

- [ ] **Step 5: Commit**

Skip unless the user asks.

---

### Task 4: GUI and docs

**Files:**
- Modify: `src/main/java/territorygame/gui/BoardPanel.java`
- Modify: `CANDIDATE_GUIDE.md`
- Modify: `README.md`
- Delete: `src/main/java/territorygame/api/CellViewType.java` if still present

**Interfaces:**
- Consumes: `GameSnapshot.CellSnapshot.territoryOwner()`, `trailOwner()`
- Produces: layered paint; candidate docs describe two enums

- [ ] **Step 1: No automated paint test**

`BoardPanel` has no render tests. Verify by `./mvnw -q test` after the paint/docs change.

- [ ] **Step 2: Paint layers**

Territory fill first (`FREE_COLOR` or `TERRITORY_COLORS[owner]`). If `trailOwner != null`, fill a smaller inner square with `TRAIL_COLORS[owner]`. Agents and visibility boxes unchanged.

- [ ] **Step 3: Docs**

Replace `VisibleCell` / `CellViewType` in `CANDIDATE_GUIDE.md` with both enums and the pair-of-layers rule. `ObservedBoard.get` returns `Optional<VisibleCell>`. README package list: `OccupantView`, `TerritoryView`.

- [ ] **Step 4: Full test run**

Run: `./mvnw -q test`

Expected: BUILD SUCCESS, no `CellViewType` leftovers (`rg CellViewType --glob '!docs/**'`).

- [ ] **Step 5: Commit**

Skip unless the user asks.
