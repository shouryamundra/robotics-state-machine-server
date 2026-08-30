# Safety Grid State Machine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `candidate/examples/SafetyGridStateMachine.java`, a new candidate-facing reference controller that plays a genuinely decent, readable strategy — unlike the existing deliberately-weak examples — bounded by a configurable "safety grid" so it can never be caught out with no safe way home.

**Architecture:** A single-file `AgentController` with six states (`ATTACK`, `AVOID`, `REPOSITION`, `OUT`, `ACROSS`, `BACK`) re-decided every turn, highest priority first. The safety-grid math and direction arithmetic are pure `static` functions with no `GameApi` dependency, so they get real unit tests; the `GameApi`-driven orchestration is validated by one growing self-play smoke test, matching this codebase's existing convention (`BasicStateMachineTest`) for controller-level testing.

**Tech Stack:** Java 21, JUnit 5 (`junit-jupiter`), Maven (`./mvnw`).

## Global Constraints

- Single new file for the controller (`candidate/examples/SafetyGridStateMachine.java`); no new helper classes — matches the existing example files' one-file-per-controller convention.
- No `Random` field — the strategy is fully deterministic, so the constructor takes no seed (matches `BasicStateMachine`/`RandomStateMachine`, registered via `seed -> new SafetyGridStateMachine()`).
- `VisibleCell` has independent `occupant()` (`OccupantView`) and `territory()` (`TerritoryView`) — there is no more combined `CellViewType`.
- `getActiveTrail()` returns the trail oldest-first, and its last element is the agent's current position whenever the trail is non-empty (confirmed via `MoveResolver`/`Agent`).
- Full spec: `docs/superpowers/specs/2026-08-29-safety-grid-state-machine-design.md`.

---

## Task 1: Pure safety-grid and direction math

**Files:**
- Create: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Create: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

**Interfaces:**
- Consumes: `territorygame.api.Direction`, `territorygame.api.GridPosition`, `territorygame.helpers.MovementUtils.nextPosition(GridPosition, Direction)`.
- Produces (package-private `static`, used by later tasks):
  - `int chebyshevDistance(GridPosition a, GridPosition b)`
  - `boolean fitsSafetyGrid(GridPosition head, List<GridPosition> trail, int gridSize)`
  - `Direction opposite(Direction direction)`
  - `List<Direction> perpendicularOptions(Direction direction)`
  - `GridPosition mirrorBack(GridPosition position, Direction outDirection, int steps)`
  - `boolean isVertical(Direction direction)`
  - `boolean isOnSideOf(GridPosition cellPosition, GridPosition agentPosition, Direction direction)`
  - `boolean isWestOfMidline(GridPosition position, int boardWidth)`
  - `boolean isOnHomeHalf(GridPosition currentPosition, GridPosition respawnPosition, int boardWidth)`
  - `Direction enemyHalfDirection(GridPosition respawnPosition, int boardWidth)`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`:

```java
package candidate.examples;

import org.junit.jupiter.api.Test;
import territorygame.api.Direction;
import territorygame.api.GridPosition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyGridStateMachineTest {

    @Test
    void chebyshevDistanceIsTheLargerAxisDelta() {
        assertEquals(3, SafetyGridStateMachine.chebyshevDistance(new GridPosition(0, 0), new GridPosition(3, 1)));
        assertEquals(2, SafetyGridStateMachine.chebyshevDistance(new GridPosition(5, 5), new GridPosition(4, 3)));
        assertEquals(0, SafetyGridStateMachine.chebyshevDistance(new GridPosition(2, 2), new GridPosition(2, 2)));
    }

    @Test
    void emptyTrailAlwaysFitsRegardlessOfGridSize() {
        assertTrue(SafetyGridStateMachine.fitsSafetyGrid(new GridPosition(10, 10), List.of(), 1));
    }

    @Test
    void trailFitsWhenEveryCellIsWithinHalfTheGridSizeOfTheHead() {
        GridPosition head = new GridPosition(5, 5);
        List<GridPosition> trail = List.of(new GridPosition(5, 4), new GridPosition(5, 5));

        assertTrue(SafetyGridStateMachine.fitsSafetyGrid(head, trail, 5)); // half = 2, distance = 1
    }

    @Test
    void trailDoesNotFitWhenAnyCellExceedsHalfTheGridSize() {
        GridPosition head = new GridPosition(5, 5);
        List<GridPosition> trail = List.of(new GridPosition(2, 5), new GridPosition(5, 5)); // distance 3

        assertFalse(SafetyGridStateMachine.fitsSafetyGrid(head, trail, 5)); // half = 2
    }

    @Test
    void oppositeReturnsTheReverseCardinalDirection() {
        assertEquals(Direction.SOUTH, SafetyGridStateMachine.opposite(Direction.NORTH));
        assertEquals(Direction.NORTH, SafetyGridStateMachine.opposite(Direction.SOUTH));
        assertEquals(Direction.WEST, SafetyGridStateMachine.opposite(Direction.EAST));
        assertEquals(Direction.EAST, SafetyGridStateMachine.opposite(Direction.WEST));
    }

    @Test
    void perpendicularOptionsForNorthSouthAreEastWest() {
        assertEquals(List.of(Direction.EAST, Direction.WEST), SafetyGridStateMachine.perpendicularOptions(Direction.NORTH));
        assertEquals(List.of(Direction.EAST, Direction.WEST), SafetyGridStateMachine.perpendicularOptions(Direction.SOUTH));
    }

    @Test
    void perpendicularOptionsForEastWestAreNorthSouth() {
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), SafetyGridStateMachine.perpendicularOptions(Direction.EAST));
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), SafetyGridStateMachine.perpendicularOptions(Direction.WEST));
    }

    @Test
    void mirrorBackWalksTheOppositeOfOutDirectionForStepsOutSteps() {
        GridPosition position = new GridPosition(5, 3); // 2 north, then 3 east of a (5,5) start
        GridPosition mirrored = SafetyGridStateMachine.mirrorBack(position, Direction.NORTH, 2);

        assertEquals(new GridPosition(5, 5), mirrored); // 2 steps south (opposite of north) from (5,3)
    }

    @Test
    void mirrorBackWithZeroStepsReturnsTheSamePosition() {
        GridPosition position = new GridPosition(7, 7);

        assertEquals(position, SafetyGridStateMachine.mirrorBack(position, Direction.EAST, 0));
    }

    @Test
    void isVerticalIsTrueOnlyForNorthAndSouth() {
        assertTrue(SafetyGridStateMachine.isVertical(Direction.NORTH));
        assertTrue(SafetyGridStateMachine.isVertical(Direction.SOUTH));
        assertFalse(SafetyGridStateMachine.isVertical(Direction.EAST));
        assertFalse(SafetyGridStateMachine.isVertical(Direction.WEST));
    }

    @Test
    void isOnSideOfChecksTheRelevantAxisOnly() {
        GridPosition agent = new GridPosition(5, 5);

        assertTrue(SafetyGridStateMachine.isOnSideOf(new GridPosition(5, 2), agent, Direction.NORTH));
        assertFalse(SafetyGridStateMachine.isOnSideOf(new GridPosition(5, 8), agent, Direction.NORTH));
        assertTrue(SafetyGridStateMachine.isOnSideOf(new GridPosition(9, 5), agent, Direction.EAST));
        assertFalse(SafetyGridStateMachine.isOnSideOf(new GridPosition(1, 5), agent, Direction.EAST));
    }

    @Test
    void isWestOfMidlineComparesXToHalfBoardWidth() {
        assertTrue(SafetyGridStateMachine.isWestOfMidline(new GridPosition(4, 0), 50));
        assertFalse(SafetyGridStateMachine.isWestOfMidline(new GridPosition(45, 0), 50));
    }

    @Test
    void isOnHomeHalfIsTrueWhenCurrentAndRespawnAreOnTheSameSide() {
        GridPosition respawn = new GridPosition(4, 25);

        assertTrue(SafetyGridStateMachine.isOnHomeHalf(new GridPosition(10, 25), respawn, 50));
        assertFalse(SafetyGridStateMachine.isOnHomeHalf(new GridPosition(40, 25), respawn, 50));
    }

    @Test
    void enemyHalfDirectionPointsAwayFromRespawnSide() {
        assertEquals(Direction.EAST, SafetyGridStateMachine.enemyHalfDirection(new GridPosition(4, 25), 50));
        assertEquals(Direction.WEST, SafetyGridStateMachine.enemyHalfDirection(new GridPosition(45, 25), 50));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail to compile**

Run: `./mvnw test -Dtest=SafetyGridStateMachineTest`
Expected: compile error — `SafetyGridStateMachine` does not exist yet.

- [ ] **Step 3: Create the class with the pure helpers**

Create `src/main/java/candidate/examples/SafetyGridStateMachine.java`:

```java
package candidate.examples;

import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GameApi;
import territorygame.api.GridPosition;
import territorygame.helpers.MovementUtils;

import java.util.List;

/**
 * A cautious, methodical reference strategy: unlike {@code BasicStateMachine}
 * and {@code RandomStateMachine}, this one is meant to actually be studied.
 * It grows territory in small rectangular bites that always stay inside a
 * configurable box around its own head — so by construction it can never be
 * caught out in the open with no safe way home — disengages the instant the
 * opponent is sighted, and only fights when the opponent trespasses onto its
 * own land. See {@code docs/superpowers/specs/2026-08-29-safety-grid-state-machine-design.md}.
 */
public final class SafetyGridStateMachine implements AgentController {

    @Override
    public void takeTurn(GameApi game) {
        game.move(Direction.NORTH); // replaced in a later task
    }

    // ---- Pure helpers (no GameApi; unit-testable directly) ---------------

    static int chebyshevDistance(GridPosition a, GridPosition b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    /** Every cell in {@code trail} must stay within {@code gridSize / 2} of {@code head}. Vacuously true for an empty trail. */
    static boolean fitsSafetyGrid(GridPosition head, List<GridPosition> trail, int gridSize) {
        int half = gridSize / 2;
        for (GridPosition cell : trail) {
            if (chebyshevDistance(cell, head) > half) {
                return false;
            }
        }
        return true;
    }

    static Direction opposite(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }

    static List<Direction> perpendicularOptions(Direction direction) {
        return switch (direction) {
            case NORTH, SOUTH -> List.of(Direction.EAST, Direction.WEST);
            case EAST, WEST -> List.of(Direction.NORTH, Direction.SOUTH);
        };
    }

    /** Walks {@code steps} cells in the reverse of {@code outDirection} from {@code position}. */
    static GridPosition mirrorBack(GridPosition position, Direction outDirection, int steps) {
        GridPosition result = position;
        Direction reverse = opposite(outDirection);
        for (int i = 0; i < steps; i++) {
            result = MovementUtils.nextPosition(result, reverse);
        }
        return result;
    }

    static boolean isVertical(Direction direction) {
        return direction == Direction.NORTH || direction == Direction.SOUTH;
    }

    static boolean isOnSideOf(GridPosition cellPosition, GridPosition agentPosition, Direction direction) {
        return switch (direction) {
            case NORTH -> cellPosition.y() < agentPosition.y();
            case SOUTH -> cellPosition.y() > agentPosition.y();
            case EAST -> cellPosition.x() > agentPosition.x();
            case WEST -> cellPosition.x() < agentPosition.x();
        };
    }

    static boolean isWestOfMidline(GridPosition position, int boardWidth) {
        return position.x() < boardWidth / 2;
    }

    static boolean isOnHomeHalf(GridPosition currentPosition, GridPosition respawnPosition, int boardWidth) {
        return isWestOfMidline(currentPosition, boardWidth) == isWestOfMidline(respawnPosition, boardWidth);
    }

    static Direction enemyHalfDirection(GridPosition respawnPosition, int boardWidth) {
        return isWestOfMidline(respawnPosition, boardWidth) ? Direction.EAST : Direction.WEST;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=SafetyGridStateMachineTest`
Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/candidate/examples/SafetyGridStateMachine.java src/test/java/candidate/examples/SafetyGridStateMachineTest.java
git commit -m "Add pure safety-grid and direction math for SafetyGridStateMachine"
```

---

## Task 2: Wire in a legal-move fallback, register it, and add the smoke test

This gets the controller into a playable (if strategically dumb) state and stands up the end-to-end regression harness the remaining tasks will keep passing.

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Modify: `src/main/java/territorygame/controller/AvailableControllers.java`
- Modify: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

**Interfaces:**
- Consumes: `territorygame.helpers.MovementUtils.validDirections(GameApi)`, `territorygame.controller.AvailableControllers.ControllerOption`.
- Produces: `Direction fallback(GameApi game)` (private instance method, used by every later `pick*` method as the empty-candidates case), `String getDebugState()`.

- [ ] **Step 1: Replace the stub `takeTurn` and add `fallback`/`getDebugState`**

In `src/main/java/candidate/examples/SafetyGridStateMachine.java`, add the import `territorygame.helpers.MovementUtils` is already present; add `java.util.List` already present. Replace:

```java
    @Override
    public void takeTurn(GameApi game) {
        game.move(Direction.NORTH); // replaced in a later task
    }
```

with:

```java
    @Override
    public void takeTurn(GameApi game) {
        game.move(fallback(game));
    }

    @Override
    public String getDebugState() {
        return "FALLBACK"; // replaced with the real Phase enum in a later task
    }

    private Direction fallback(GameApi game) {
        List<Direction> valid = MovementUtils.validDirections(game);
        return valid.isEmpty() ? Direction.NORTH : valid.get(0);
    }
```

- [ ] **Step 2: Register it in the GUI's controller picker**

In `src/main/java/territorygame/controller/AvailableControllers.java`, add an import. Replace:

```java
import candidate.CandidateController;
import candidate.examples.BasicStateMachine;
import candidate.examples.RandomStateMachine;
import territorygame.api.AgentController;
```

with:

```java
import candidate.CandidateController;
import candidate.examples.BasicStateMachine;
import candidate.examples.RandomStateMachine;
import candidate.examples.SafetyGridStateMachine;
import territorygame.api.AgentController;
```

Then replace:

```java
    public static final List<ControllerOption> ALL = List.of(
            new ControllerOption("Basic State Machine", seed -> new BasicStateMachine()),
            new ControllerOption("Enemy State Machine", EnemyStateMachine::new),
            new ControllerOption("Random State Machine", seed -> new RandomStateMachine()),
            new ControllerOption("Candidate Controller", seed -> new CandidateController())
    );
```

with:

```java
    public static final List<ControllerOption> ALL = List.of(
            new ControllerOption("Basic State Machine", seed -> new BasicStateMachine()),
            new ControllerOption("Safety Grid State Machine", seed -> new SafetyGridStateMachine()),
            new ControllerOption("Enemy State Machine", EnemyStateMachine::new),
            new ControllerOption("Random State Machine", seed -> new RandomStateMachine()),
            new ControllerOption("Candidate Controller", seed -> new CandidateController())
    );
```

- [ ] **Step 3: Write the failing smoke test**

In `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`, replace the import block:

```java
package candidate.examples;

import org.junit.jupiter.api.Test;
import territorygame.api.Direction;
import territorygame.api.GridPosition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

with:

```java
package candidate.examples;

import org.junit.jupiter.api.Test;
import territorygame.api.AgentController;
import territorygame.api.Direction;
import territorygame.api.GridPosition;
import territorygame.domain.GameConfig;
import territorygame.engine.GameEngine;
import territorygame.engine.GameSnapshot;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

Then add this test method to the class:

```java
    @Test
    void playsManyTurnsAgainstItselfWithoutFrameworkErrors() throws InterruptedException {
        GameConfig config = new GameConfig(
                20, 20, 11, 40,
                List.of(new GridPosition(4, 10), new GridPosition(15, 10)),
                3, 0, // no auto-play delay in tests
                20, List.of(1L, 2L)
        );
        List<AgentController> controllers = List.of(
                new SafetyGridStateMachine(),
                new SafetyGridStateMachine()
        );
        GameEngine engine = new GameEngine(config, controllers);
        BlockingQueue<GameSnapshot> snapshots = new LinkedBlockingQueue<>();
        engine.addObserver(snapshots::add);
        engine.reset(controllers);
        assertNotNull(snapshots.poll(2, TimeUnit.SECONDS));

        engine.start();

        GameSnapshot last = null;
        for (int i = 0; i < 80; i++) { // 40 turns per player, 2 players
            GameSnapshot snapshot = snapshots.poll(2, TimeUnit.SECONDS);
            assertNotNull(snapshot, "engine stalled or threw before completing all turns");
            last = snapshot;
        }

        assertTrue(last.gameOver());
    }
```

This mirrors `BasicStateMachineTest` exactly (same config, same polling pattern) — see `src/test/java/candidate/examples/BasicStateMachineTest.java` for the pattern this is copied from.

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -Dtest=SafetyGridStateMachineTest`
Expected: PASS, 15 tests (14 from Task 1 plus this one). The fallback strategy can legitimately kill itself on its own trail (like `RandomStateMachine`) — that's fine, `DIED` just respawns it; the test only asserts the engine keeps running without stalling or throwing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/candidate/examples/SafetyGridStateMachine.java src/main/java/territorygame/controller/AvailableControllers.java src/test/java/candidate/examples/SafetyGridStateMachineTest.java
git commit -m "Wire SafetyGridStateMachine into the GUI and add the self-play smoke test"
```

---

## Task 3: Board-reading helpers, plus ATTACK, AVOID, and REPOSITION

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`

**Interfaces:**
- Consumes: `territorygame.api.OccupantView`, `territorygame.api.TerritoryView`, `territorygame.api.VisibleCell`, `territorygame.api.MoveResult` (not needed directly), `territorygame.helpers.MovementUtils.{findCell, isValidBoardMove, isWithinBoard, manhattanDistance}`, `fitsSafetyGrid`/`isOnHomeHalf`/`enemyHalfDirection`/`isOnSideOf`/`isVertical` from Task 1, `fallback` from Task 2.
- Produces (private instance methods, consumed by Task 4):
  - `List<Direction> safeDirections(GameApi game)`
  - `List<Direction> huntableDirections(GameApi game)`
  - `GridPosition destination(GameApi game, Direction direction)`
  - `boolean isSelfTerritory(GameApi game, GridPosition position)`
  - `Direction chooseBest(GameApi game, List<Direction> candidates, Comparator<Direction> ranking)`
  - `Comparator<Direction> distanceTo(GameApi game, GridPosition target)`
  - `Comparator<Direction> mostOpenFirst(GameApi game)`
  - `Optional<GridPosition> nearestKnownSelfTerritory(GameApi game)`
  - `Direction pickBack(GameApi game)`

**Step 1 is a design decision, not code:** these methods depend on `GameApi`, which has no test double in this codebase (no other controller — `EnemyStateMachine` included — unit-tests its `GameApi`-driven logic in isolation; they're all validated by full-match smoke tests instead). Building one would be new, non-trivial test infrastructure the design spec didn't call for. This task's correctness is validated by the smoke test at the end, matching that existing convention.

- [ ] **Step 1: Add the imports**

In `src/main/java/candidate/examples/SafetyGridStateMachine.java`, add:

```java
import territorygame.api.OccupantView;
import territorygame.api.TerritoryView;
import territorygame.api.VisibleCell;

import java.util.Comparator;
import java.util.Optional;
```

- [ ] **Step 2: Add the board-reading helpers**

Add these private methods (anywhere below `fallback`):

```java
    private List<Direction> safeDirections(GameApi game) {
        return List.of(Direction.values()).stream()
                .filter(direction -> MovementUtils.isValidBoardMove(
                        game.getAgentPosition(), direction, game.getBoardWidth(), game.getBoardHeight()))
                .filter(direction -> {
                    OccupantView occupant = occupantAt(game, destination(game, direction));
                    return occupant != OccupantView.SELF_TRAIL
                            && occupant != OccupantView.OPPONENT_TRAIL
                            && occupant != OccupantView.OPPONENT_AGENT;
                })
                .toList();
    }

    /** Like {@link #safeDirections}, but allows stepping onto the opponent's trail — that's how a chase ends in a kill. */
    private List<Direction> huntableDirections(GameApi game) {
        return List.of(Direction.values()).stream()
                .filter(direction -> MovementUtils.isValidBoardMove(
                        game.getAgentPosition(), direction, game.getBoardWidth(), game.getBoardHeight()))
                .filter(direction -> {
                    OccupantView occupant = occupantAt(game, destination(game, direction));
                    return occupant != OccupantView.SELF_TRAIL && occupant != OccupantView.OPPONENT_AGENT;
                })
                .toList();
    }

    private OccupantView occupantAt(GameApi game, GridPosition position) {
        return MovementUtils.findCell(game.getVisibleGrid(), position)
                .map(VisibleCell::occupant)
                .orElse(OccupantView.EMPTY);
    }

    private GridPosition destination(GameApi game, Direction direction) {
        return MovementUtils.nextPosition(game.getAgentPosition(), direction);
    }

    /** {@code false} for cells outside the visible window — use {@link #isKnownSelfTerritory} when that matters. */
    private boolean isSelfTerritory(GameApi game, GridPosition position) {
        return MovementUtils.findCell(game.getVisibleGrid(), position)
                .map(cell -> cell.territory() == TerritoryView.SELF)
                .orElse(false);
    }

    private Direction chooseBest(GameApi game, List<Direction> candidates, Comparator<Direction> ranking) {
        if (candidates.isEmpty()) {
            return fallback(game);
        }
        return candidates.stream().min(ranking).orElseThrow();
    }

    private Comparator<Direction> distanceTo(GameApi game, GridPosition target) {
        return Comparator.comparingInt(direction -> MovementUtils.manhattanDistance(destination(game, direction), target));
    }

    /** Counts visible unclaimed cells on each side of the agent; NORTH/SOUTH counts are weighted higher. */
    private int opennessScore(GameApi game, Direction direction) {
        GridPosition agentPosition = game.getAgentPosition();
        int count = 0;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.territory() == TerritoryView.UNOWNED && isOnSideOf(cell.position(), agentPosition, direction)) {
                    count++;
                }
            }
        }
        return isVertical(direction) ? count * VERTICAL_WEIGHT : count;
    }

    private Comparator<Direction> mostOpenFirst(GameApi game) {
        return Comparator.comparingInt((Direction direction) -> opennessScore(game, direction)).reversed();
    }

    private Optional<GridPosition> nearestKnownSelfTerritory(GameApi game) {
        GridPosition from = game.getAgentPosition();
        GridPosition best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.territory() == TerritoryView.SELF) {
                    int distance = MovementUtils.manhattanDistance(from, cell.position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cell.position();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Stays inside our own territory if any safe move lands there; otherwise heads for the nearest known owned cell. */
    private Direction pickBack(GameApi game) {
        List<Direction> candidates = safeDirections(game);
        List<Direction> withinTerritory = candidates.stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (!withinTerritory.isEmpty()) {
            return chooseBest(game, withinTerritory, mostOpenFirst(game));
        }
        GridPosition target = nearestKnownSelfTerritory(game).orElse(game.getRespawnPosition());
        return chooseBest(game, candidates, distanceTo(game, target));
    }
```

This references `VERTICAL_WEIGHT`, added in the next step.

- [ ] **Step 3: Add the opponent-sighting/intrusion lookups, the visible-territory fraction, and the state constants**

Add these private methods:

```java
    private Optional<GridPosition> findOpponentPosition(GameApi game) {
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.occupant() == OccupantView.OPPONENT_AGENT) {
                    return Optional.of(cell.position());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isOpponentVisible(GameApi game) {
        return findOpponentPosition(game).isPresent();
    }

    /** Nearest visible cell where the opponent's trail is crossing land that's ours — an intrusion worth punishing. */
    private Optional<GridPosition> findOpponentTrailOnOurTerritory(GameApi game) {
        GridPosition from = game.getAgentPosition();
        GridPosition best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                if (cell.territory() == TerritoryView.SELF && cell.occupant() == OccupantView.OPPONENT_TRAIL) {
                    int distance = MovementUtils.manhattanDistance(from, cell.position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cell.position();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private double visibleTerritoryFraction(GameApi game) {
        int total = 0;
        int selfTerritory = 0;
        for (VisibleCell[] row : game.getVisibleGrid()) {
            for (VisibleCell cell : row) {
                total++;
                if (cell.territory() == TerritoryView.SELF) {
                    selfTerritory++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) selfTerritory / total;
    }
```

Add these constants right above the `Phase` enum (add the enum now too, even though `OUT`/`ACROSS`/`BACK` aren't used until Task 4 — an enum with unused values compiles cleanly):

```java
    private static final int AWAY_GRID_SIZE = 5;
    private static final int HOME_GRID_SIZE = 9;
    private static final double TERRITORY_VISIBLE_THRESHOLD = 0.90;
    private static final int VERTICAL_WEIGHT = 2;

    private enum Phase {
        ATTACK, AVOID, REPOSITION, OUT, ACROSS, BACK
    }

    private Phase phase = Phase.OUT;
```

- [ ] **Step 4: Wire ATTACK, AVOID, and REPOSITION into `takeTurn`, and add their `pick*` methods**

Replace:

```java
    @Override
    public void takeTurn(GameApi game) {
        game.move(fallback(game));
    }

    @Override
    public String getDebugState() {
        return "FALLBACK"; // replaced with the real Phase enum in a later task
    }
```

with:

```java
    @Override
    public void takeTurn(GameApi game) {
        Optional<GridPosition> intrusion = findOpponentTrailOnOurTerritory(game);
        Direction direction;
        if (intrusion.isPresent()) {
            phase = Phase.ATTACK;
            direction = pickAttack(game, intrusion.get());
        } else if (isOpponentVisible(game)) {
            phase = Phase.AVOID;
            direction = pickAvoid(game);
        } else if (game.getActiveTrail().isEmpty() && visibleTerritoryFraction(game) >= TERRITORY_VISIBLE_THRESHOLD) {
            phase = Phase.REPOSITION;
            direction = pickReposition(game);
        } else {
            direction = fallback(game); // replaced with pickExpedition(game) in Task 4
        }
        game.move(direction);
    }

    @Override
    public String getDebugState() {
        return phase.name();
    }

    /** A free kill: crossing their trail sends them back to respawn, no risk to us. */
    private Direction pickAttack(GameApi game, GridPosition target) {
        return chooseBest(game, huntableDirections(game), distanceTo(game, target));
    }

    /** Retreats if mid-expedition; if already home, kites away from the opponent while staying on our own territory. */
    private Direction pickAvoid(GameApi game) {
        if (!game.getActiveTrail().isEmpty()) {
            return pickBack(game);
        }
        GridPosition opponentPosition = findOpponentPosition(game).orElseThrow();
        List<Direction> territoryOnly = safeDirections(game).stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (territoryOnly.isEmpty()) {
            return pickBack(game);
        }
        Comparator<Direction> farthestFirst = Comparator.comparingInt(
                (Direction direction) -> -MovementUtils.manhattanDistance(destination(game, direction), opponentPosition));
        return chooseBest(game, territoryOnly, farthestFirst.thenComparing(mostOpenFirst(game)));
    }

    /** Walks toward the opponent's half, preferring moves that stay on our own territory (so no trail risk is taken). */
    private Direction pickReposition(GameApi game) {
        Direction towardEnemy = enemyHalfDirection(game.getRespawnPosition(), game.getBoardWidth());
        List<Direction> candidates = safeDirections(game);
        List<Direction> territoryOnly = candidates.stream()
                .filter(direction -> isSelfTerritory(game, destination(game, direction)))
                .toList();
        if (!territoryOnly.isEmpty()) {
            candidates = territoryOnly;
        }
        if (candidates.contains(towardEnemy)) {
            return towardEnemy;
        }
        return chooseBest(game, candidates, mostOpenFirst(game));
    }
```

- [ ] **Step 5: Run the full test suite**

Run: `./mvnw test -Dtest=SafetyGridStateMachineTest`
Expected: PASS, 15 tests — the smoke test still passes (ATTACK/AVOID/REPOSITION are all safe, legal moves; anything not covered by them still falls through to the same `fallback` as before).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/candidate/examples/SafetyGridStateMachine.java
git commit -m "Add ATTACK, AVOID, and REPOSITION states to SafetyGridStateMachine"
```

---

## Task 4: The OUT/ACROSS/BACK expedition and the safety grid

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`

**Interfaces:**
- Consumes: `fitsSafetyGrid`, `mirrorBack`, `perpendicularOptions`, `isOnHomeHalf` (Task 1); `safeDirections`, `chooseBest`, `mostOpenFirst`, `isSelfTerritory`, `destination`, `pickBack` (Task 3).
- Produces: `Direction pickExpedition(GameApi game)`, consumed only by `takeTurn` in this same task (nothing later depends on it).

- [ ] **Step 1: Add the expedition fields**

Add next to `phase`/`outDirection` (create these fields right below `private Phase phase = Phase.OUT;`):

```java
    private Direction outDirection;
    private int stepsOut;
    private Direction acrossDirection;
```

- [ ] **Step 2: Add the expedition `pick*` methods**

Add these private methods:

```java
    private int gridSizeFor(GameApi game) {
        boolean home = isOnHomeHalf(game.getAgentPosition(), game.getRespawnPosition(), game.getBoardWidth());
        return home ? HOME_GRID_SIZE : AWAY_GRID_SIZE;
    }

    /** Starts a fresh expedition whenever we're standing on territory; otherwise continues whichever phase we're in. */
    private Direction pickExpedition(GameApi game) {
        if (game.getActiveTrail().isEmpty()) {
            phase = Phase.OUT;
            outDirection = chooseBest(game, safeDirections(game), mostOpenFirst(game));
            stepsOut = 0;
        }
        return switch (phase) {
            case OUT -> pickOut(game);
            case ACROSS -> pickAcross(game);
            // ATTACK/AVOID/REPOSITION never reach here; BACK, and any interrupted-then-resumed
            // phase left over from an ATTACK/AVOID detour, both just keep heading home.
            default -> pickBack(game);
        };
    }

    private Direction pickOut(GameApi game) {
        GridPosition nextHead = destination(game, outDirection);
        if (safeDirections(game).contains(outDirection) && fitsSafetyGrid(nextHead, game.getActiveTrail(), gridSizeFor(game))) {
            stepsOut++;
            return outDirection;
        }
        phase = Phase.ACROSS;
        acrossDirection = pickAcrossDirection(game);
        return pickAcross(game);
    }

    private Direction pickAcrossDirection(GameApi game) {
        List<Direction> safe = safeDirections(game);
        List<Direction> perpendicular = perpendicularOptions(outDirection).stream()
                .filter(safe::contains)
                .toList();
        if (perpendicular.isEmpty()) {
            return outDirection; // both perpendicular options are blocked; this will fail the grid/mirror check below and fall back to BACK
        }
        return chooseBest(game, perpendicular, mostOpenFirst(game));
    }

    private Direction pickAcross(GameApi game) {
        if (canTakeAnotherAcrossStep(game)) {
            return acrossDirection;
        }
        phase = Phase.BACK;
        return pickBack(game);
    }

    private boolean canTakeAnotherAcrossStep(GameApi game) {
        if (!safeDirections(game).contains(acrossDirection)) {
            return false;
        }
        GridPosition nextHead = destination(game, acrossDirection);
        if (!fitsSafetyGrid(nextHead, game.getActiveTrail(), gridSizeFor(game))) {
            return false;
        }
        GridPosition mirrored = mirrorBack(nextHead, outDirection, stepsOut);
        return isKnownSelfTerritory(game, mirrored);
    }

    /** Like {@link #isSelfTerritory}, but falls back to {@code observedBoard} for cells outside the live window. */
    private boolean isKnownSelfTerritory(GameApi game, GridPosition position) {
        if (!MovementUtils.isWithinBoard(position, game.getBoardWidth(), game.getBoardHeight())) {
            return false;
        }
        Optional<VisibleCell> live = MovementUtils.findCell(game.getVisibleGrid(), position);
        if (live.isPresent()) {
            return live.get().territory() == TerritoryView.SELF;
        }
        return observedBoard.get(position)
                .map(cell -> cell.territory() == TerritoryView.SELF)
                .orElse(false);
    }
```

- [ ] **Step 3: Add the `ObservedBoard` field, initialize and update it, and wire `pickExpedition` into `takeTurn`**

Add the import:

```java
import territorygame.helpers.ObservedBoard;
```

Add the field next to `acrossDirection`:

```java
    private ObservedBoard observedBoard;
```

In `takeTurn`, add the lazy-init and per-turn update right at the top, and replace the last `fallback(game)` branch with `pickExpedition(game)`:

```java
    @Override
    public void takeTurn(GameApi game) {
        if (observedBoard == null) {
            observedBoard = new ObservedBoard(game.getBoardWidth(), game.getBoardHeight());
        }
        observedBoard.update(game.getVisibleGrid());

        Optional<GridPosition> intrusion = findOpponentTrailOnOurTerritory(game);
        Direction direction;
        if (intrusion.isPresent()) {
            phase = Phase.ATTACK;
            direction = pickAttack(game, intrusion.get());
        } else if (isOpponentVisible(game)) {
            phase = Phase.AVOID;
            direction = pickAvoid(game);
        } else if (game.getActiveTrail().isEmpty() && visibleTerritoryFraction(game) >= TERRITORY_VISIBLE_THRESHOLD) {
            phase = Phase.REPOSITION;
            direction = pickReposition(game);
        } else {
            direction = pickExpedition(game);
        }
        game.move(direction);
    }
```

- [ ] **Step 4: Run the full test suite**

Run: `./mvnw test -Dtest=SafetyGridStateMachineTest`
Expected: PASS, 15 tests. The controller now uses every state from the spec; the smoke test confirms two instances can still play a full match against each other without stalling, throwing, or the engine's per-turn retry cap (`turn.maxAttemptsPerTurn`) ever being exhausted.

- [ ] **Step 5: Manually sanity-check a match in the GUI**

Run: `./mvnw compile exec:java`, pick "Safety Grid State Machine" for both player slots, hit Start, and watch a few minutes of play. Confirm territory grows in visible rectangular bites, and that `getDebugState()`'s label next to each player card cycles through the phases as expected (mostly `OUT`/`ACROSS`/`BACK`, occasionally `AVOID` when the two agents spot each other). This step has no automated pass/fail condition — it's a spot-check, not a substitute for Step 4.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/candidate/examples/SafetyGridStateMachine.java
git commit -m "Add the OUT/ACROSS/BACK expedition and safety grid to SafetyGridStateMachine"
```

---

## Task 5: Documentation

**Files:**
- Modify: `CANDIDATE_GUIDE.md`
- Modify: `README.md`

- [ ] **Step 1: Update `CANDIDATE_GUIDE.md`'s intro**

In `CANDIDATE_GUIDE.md`, replace:

```
Inside `candidate/`, `examples/` has a couple of read-only implementations
worth studying. There's also a more advanced opponent to practice against,
shown as "Enemy State Machine" in the GUI.
```

with:

```
Inside `candidate/`, `examples/` has a few read-only implementations.
`BasicStateMachine` and `RandomStateMachine` only demonstrate the mechanics
— not a strategy worth copying. `SafetyGridStateMachine` is simple but
genuinely effective, and worth studying. There's also a more advanced
opponent to practice against, shown as "Enemy State Machine" in the GUI.
```

- [ ] **Step 2: Update `README.md`'s GUI-picker sentence**

In `README.md`, replace:

```
In the GUI, pick which controller occupies each player slot (Basic State
Machine, Enemy State Machine, Random State Machine, or the candidate's own
controller), then use Start / Pause / Step / Reset to run a match.
```

with:

```
In the GUI, pick which controller occupies each player slot (Basic State
Machine, Safety Grid State Machine, Enemy State Machine, Random State
Machine, or the candidate's own controller), then use Start / Pause / Step
/ Reset to run a match.
```

- [ ] **Step 3: Update `README.md`'s project-structure listing**

In `README.md`, replace:

```
src/main/java/candidate/
  CandidateController.java   the file to edit for the assessment.

  examples/                  Read-only reference controllers: BasicStateMachine
                              (a deliberately weak state-machine example) and
                              RandomStateMachine (the simplest possible baseline).
                              Not templates for a good strategy.
```

with:

```
src/main/java/candidate/
  CandidateController.java   the file to edit for the assessment.

  examples/                  Read-only reference controllers: BasicStateMachine
                              (a deliberately weak state-machine example) and
                              RandomStateMachine (the simplest possible baseline)
                              are not templates for a good strategy.
                              SafetyGridStateMachine is — simple, but a
                              genuinely effective one worth studying.
```

- [ ] **Step 4: Commit**

```bash
git add CANDIDATE_GUIDE.md README.md
git commit -m "Document SafetyGridStateMachine as a candidate-facing example worth studying"
```

---

## Task 6: Final verification

- [ ] **Step 1: Run the full test suite**

Run: `./mvnw test`
Expected: PASS, all tests across the whole project (not just this feature's), confirming nothing else was broken.

- [ ] **Step 2: Confirm the working tree is clean**

Run: `git status`
Expected: nothing to commit, working tree clean (everything was committed at the end of each prior task).
