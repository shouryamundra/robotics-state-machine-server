# Safety Grid Edge Expeditions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reposition within owned territory until reaching its edge, then start expeditions toward the greatest amount of remembered unowned space with vertical-only tie-breaking.

**Architecture:** Keep the existing single-controller state machine and add small package-private pure helpers for edge detection and direction ranking. `ObservedBoard` remains the sole long-term board memory; state priority and expedition mechanics remain unchanged.

**Tech Stack:** Java 21, JUnit Jupiter 5.10.2, Maven

## Global Constraints

- ATTACK and AVOID retain their current priority and behavior.
- Unknown cells do not contribute to openness.
- Prefer vertical directions only when openness counts are equal; do not multiply scores.
- Do not add dependencies or refactor unrelated controller behavior.
- Do not create git commits unless the user explicitly requests them.

---

### Task 1: Detect territory edges and select REPOSITION

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Test: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

**Interfaces:**
- Produces: `static boolean isTerritoryEdge(GridPosition position, VisibleCell[][] visibleGrid, int boardWidth, int boardHeight)`
- Consumes: `MovementUtils.nextPosition`, `MovementUtils.isWithinBoard`, and the live visible grid

- [ ] **Step 1: Write failing edge-detection tests**

Add tests that construct live grids containing the current cell and its cardinal neighbors:

```java
@Test
void ownedCellIsAnEdgeWhenAnInBoundsNeighborIsNotOwned() {
    GridPosition position = new GridPosition(2, 2);
    VisibleCell[][] grid = crossGrid(position, TerritoryView.SELF);
    grid[1][2] = new VisibleCell(new GridPosition(2, 1), OccupantView.EMPTY, TerritoryView.UNOWNED);

    assertTrue(SafetyGridStateMachine.isTerritoryEdge(position, grid, 5, 5));
}

@Test
void ownedCellIsNotAnEdgeWhenEveryInBoundsNeighborIsOwned() {
    GridPosition position = new GridPosition(2, 2);

    assertFalse(SafetyGridStateMachine.isTerritoryEdge(
            position, crossGrid(position, TerritoryView.SELF), 5, 5));
}
```

Add this test helper:

```java
private static VisibleCell[][] crossGrid(GridPosition center, TerritoryView territory) {
    VisibleCell[][] grid = new VisibleCell[5][5];
    for (int y = 0; y < 5; y++) {
        for (int x = 0; x < 5; x++) {
            grid[y][x] = new VisibleCell(
                    new GridPosition(x, y), OccupantView.EMPTY, territory);
        }
    }
    return grid;
}
```

Add static imports or API imports for `OccupantView`, `TerritoryView`, and `VisibleCell`.

- [ ] **Step 2: Verify the tests fail for the missing helper**

Run:

```bash
mvn -q -Dtest=SafetyGridStateMachineTest test
```

Expected: compilation failure because `isTerritoryEdge` does not exist.

- [ ] **Step 3: Implement edge detection and state selection**

Add the helper:

```java
static boolean isTerritoryEdge(
        GridPosition position,
        VisibleCell[][] visibleGrid,
        int boardWidth,
        int boardHeight
) {
    Optional<VisibleCell> current = MovementUtils.findCell(visibleGrid, position);
    if (current.isEmpty() || current.get().territory() != TerritoryView.SELF) {
        return false;
    }
    for (Direction direction : Direction.values()) {
        GridPosition neighbor = MovementUtils.nextPosition(position, direction);
        if (!MovementUtils.isWithinBoard(neighbor, boardWidth, boardHeight)) {
            continue;
        }
        if (MovementUtils.findCell(visibleGrid, neighbor)
                .map(cell -> cell.territory() != TerritoryView.SELF)
                .orElse(false)) {
            return true;
        }
    }
    return false;
}
```

Replace the percentage-based REPOSITION branch in `takeTurn` with:

```java
} else if (game.getActiveTrail().isEmpty()
        && !isTerritoryEdge(
                game.getAgentPosition(),
                game.getVisibleGrid(),
                game.getBoardWidth(),
                game.getBoardHeight())) {
    phase = Phase.REPOSITION;
    direction = pickReposition(game);
```

Delete `TERRITORY_VISIBLE_THRESHOLD` and `visibleTerritoryFraction`.

- [ ] **Step 4: Verify edge tests and the existing suite pass**

Run:

```bash
mvn -q -Dtest=SafetyGridStateMachineTest test
mvn -q test
```

Expected: both commands pass.

---

### Task 2: Rank directions using remembered open space

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Test: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

**Interfaces:**
- Produces: `static int opennessScore(ObservedBoard board, GridPosition position, Direction direction, int boardWidth, int boardHeight)`
- Produces: `static Comparator<Direction> mostOpenFirst(ObservedBoard board, GridPosition position, int boardWidth, int boardHeight)`
- Consumes: the latest remembered `VisibleCell` at every board coordinate

- [ ] **Step 1: Write failing remembered-openness and tie-break tests**

Add:

```java
@Test
void opennessIncludesRememberedUnownedCellsOutsideTheCurrentView() {
    ObservedBoard board = new ObservedBoard(8, 8);
    board.update(new VisibleCell[][] {{
            new VisibleCell(new GridPosition(6, 2), OccupantView.EMPTY, TerritoryView.UNOWNED),
            new VisibleCell(new GridPosition(7, 2), OccupantView.EMPTY, TerritoryView.UNOWNED)
    }});

    assertEquals(2, SafetyGridStateMachine.opennessScore(
            board, new GridPosition(2, 2), Direction.EAST, 8, 8));
}

@Test
void opennessRankingPrefersVerticalOnlyWhenScoresTie() {
    ObservedBoard board = new ObservedBoard(8, 8);
    board.update(new VisibleCell[][] {{
            new VisibleCell(new GridPosition(2, 1), OccupantView.EMPTY, TerritoryView.UNOWNED),
            new VisibleCell(new GridPosition(1, 2), OccupantView.EMPTY, TerritoryView.UNOWNED),
            new VisibleCell(new GridPosition(4, 2), OccupantView.EMPTY, TerritoryView.UNOWNED),
            new VisibleCell(new GridPosition(5, 2), OccupantView.EMPTY, TerritoryView.UNOWNED)
    }});
    Comparator<Direction> ranking = SafetyGridStateMachine.mostOpenFirst(
            board, new GridPosition(2, 2), 8, 8);

    assertTrue(ranking.compare(Direction.NORTH, Direction.WEST) < 0);
    assertTrue(ranking.compare(Direction.EAST, Direction.NORTH) < 0);
}
```

Import `ObservedBoard` and `Comparator`.

- [ ] **Step 2: Verify the tests fail for the old API**

Run:

```bash
mvn -q -Dtest=SafetyGridStateMachineTest test
```

Expected: compilation failure because the package-private remembered-board overloads do not exist.

- [ ] **Step 3: Implement remembered scoring and vertical tie-breaking**

Replace the old weighted visible-grid scorer and comparator with:

```java
static int opennessScore(
        ObservedBoard board,
        GridPosition position,
        Direction direction,
        int boardWidth,
        int boardHeight
) {
    int count = 0;
    for (int y = 0; y < boardHeight; y++) {
        for (int x = 0; x < boardWidth; x++) {
            GridPosition cellPosition = new GridPosition(x, y);
            if (isOnSideOf(cellPosition, position, direction)
                    && board.get(cellPosition)
                            .map(cell -> cell.territory() == TerritoryView.UNOWNED)
                            .orElse(false)) {
                count++;
            }
        }
    }
    return count;
}

static Comparator<Direction> mostOpenFirst(
        ObservedBoard board,
        GridPosition position,
        int boardWidth,
        int boardHeight
) {
    return Comparator
            .comparingInt((Direction direction) ->
                    opennessScore(board, position, direction, boardWidth, boardHeight))
            .reversed()
            .thenComparingInt(direction -> isVertical(direction) ? 0 : 1);
}

private Comparator<Direction> mostOpenFirst(GameApi game) {
    return mostOpenFirst(
            observedBoard,
            game.getAgentPosition(),
            game.getBoardWidth(),
            game.getBoardHeight());
}
```

Delete `VERTICAL_WEIGHT`.

- [ ] **Step 4: Verify focused and full tests pass**

Run:

```bash
mvn -q -Dtest=SafetyGridStateMachineTest test
mvn -q test
```

Expected: both commands pass; the second test proves a horizontal score of two beats a vertical score of one while vertical wins a one-to-one tie.

---

### Task 3: Make REPOSITION use shared open-space ranking

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Test: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

**Interfaces:**
- Consumes: `mostOpenFirst(GameApi game)` from Task 2
- Produces: REPOSITION behavior with no enemy-half special case

- [ ] **Step 1: Write a failing behavioral REPOSITION test**

Add a test in which east points toward the enemy half but all openness scores tie. The expected north move proves REPOSITION uses the shared vertical tie-break instead of its old eastward override:

```java
@Test
void repositionUsesOpenSpaceRankingInsteadOfEnemyHalfOverride() {
    StubGameApi game = new StubGameApi(
            new GridPosition(2, 2),
            new GridPosition(0, 2),
            crossGrid(new GridPosition(2, 2), TerritoryView.SELF));
    SafetyGridStateMachine controller = new SafetyGridStateMachine();

    controller.takeTurn(game);

    assertEquals("REPOSITION", controller.getDebugState());
    assertEquals(Direction.NORTH, game.movedDirection);
}
```

Add this minimal test double inside `SafetyGridStateMachineTest`:

```java
private static final class StubGameApi implements GameApi {
    private final GridPosition position;
    private final GridPosition respawn;
    private final VisibleCell[][] visibleGrid;
    private Direction movedDirection;

    private StubGameApi(
            GridPosition position,
            GridPosition respawn,
            VisibleCell[][] visibleGrid
    ) {
        this.position = position;
        this.respawn = respawn;
        this.visibleGrid = visibleGrid;
    }

    @Override public GridPosition getAgentPosition() { return position; }
    @Override public GridPosition getRespawnPosition() { return respawn; }
    @Override public int getOwnedTerritoryCellCount() { return 25; }
    @Override public int getOpponentTerritoryCellCount() { return 0; }
    @Override public int getRemainingTurns() { return 1; }
    @Override public List<GridPosition> getActiveTrail() { return List.of(); }
    @Override public VisibleCell[][] getVisibleGrid() { return visibleGrid; }
    @Override public int getBoardWidth() { return 5; }
    @Override public int getBoardHeight() { return 5; }

    @Override
    public MoveResult move(Direction direction) {
        movedDirection = direction;
        return MoveResult.MOVED;
    }
}
```

Import `GameApi` and `MoveResult`.

- [ ] **Step 2: Verify the regression test fails**

Run:

```bash
mvn -q -Dtest=SafetyGridStateMachineTest#repositionUsesOpenSpaceRankingInsteadOfEnemyHalfOverride test
```

Expected: FAIL because the controller moves east toward the enemy half instead of north by the shared tie-break.

- [ ] **Step 3: Simplify REPOSITION to use the shared ranking**

Replace `pickReposition` with:

```java
private Direction pickReposition(GameApi game) {
    List<Direction> candidates = safeDirections(game);
    List<Direction> territoryOnly = candidates.stream()
            .filter(direction -> isSelfTerritory(game, destination(game, direction)))
            .toList();
    if (!territoryOnly.isEmpty()) {
        candidates = territoryOnly;
    }
    return chooseBest(game, candidates, mostOpenFirst(game));
}
```

Delete `isWestOfMidline`, `isOnHomeHalf`, and `enemyHalfDirection` only if no production behavior still uses them. Preserve `isOnHomeHalf` if `gridSizeFor` still requires it; in that case delete only `enemyHalfDirection`.

- [ ] **Step 4: Run focused and full verification**

Run:

```bash
mvn -q -Dtest=SafetyGridStateMachineTest test
mvn -q test
```

Expected: all tests pass with no warnings or framework errors.

---

### Task 4: Preserve REPOSITION direction until the edge

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Test: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

**Interfaces:**
- Produces: persistent `Direction repositionDirection`
- Consumes: existing safe owned-direction filtering and `mostOpenFirst(GameApi)`

- [ ] **Step 1: Write a failing momentum test**

Use a mutable position in `StubGameApi`. Start at `(2,3)` where tied scores prefer north. Move the test position to `(2,2)`, where rescoring favors south, and assert that the second turn still chooses north:

```java
@Test
void repositionKeepsItsDirectionWhenTheScoreFlips() {
    VisibleCell[][] grid = filledGrid(TerritoryView.SELF);
    for (int x : List.of(0, 4)) {
        grid[2][x] = new VisibleCell(new GridPosition(x, 2), OccupantView.EMPTY, TerritoryView.UNOWNED);
    }
    for (int x : List.of(0, 4)) {
        grid[4][x] = new VisibleCell(new GridPosition(x, 4), OccupantView.EMPTY, TerritoryView.UNOWNED);
    }
    StubGameApi game = new StubGameApi(new GridPosition(2, 3), new GridPosition(0, 2), grid);
    SafetyGridStateMachine controller = new SafetyGridStateMachine();

    controller.takeTurn(game);
    assertEquals(Direction.NORTH, game.movedDirection);

    game.position = new GridPosition(2, 2);
    controller.takeTurn(game);

    assertEquals(Direction.NORTH, game.movedDirection);
}
```

- [ ] **Step 2: Verify the test fails**

Run:

```bash
bash mvnw -q -Dtest=SafetyGridStateMachineTest#repositionKeepsItsDirectionWhenTheScoreFlips test
```

Expected: FAIL because the second turn rescans and chooses `SOUTH`.

- [ ] **Step 3: Add minimal REPOSITION momentum**

Add `private Direction repositionDirection;`. In `pickReposition`, retain it while it remains among safe moves landing on `SELF`; otherwise choose and store a new ranked direction. Clear it when a trail-free edge turn starts OUT.

- [ ] **Step 4: Verify focused and full tests**

Run:

```bash
bash mvnw -q -Dtest=SafetyGridStateMachineTest test
bash mvnw test
```

Expected: the focused test and full suite pass with no failures.

---

### Task 5: Keep OUT off owned territory

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Test: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

- [ ] **Step 1: Add failing behavioral tests**

Cover three cases through `takeTurn`: initial OUT may choose `OPPONENT`, an active OUT transitions to ACROSS before stepping onto `SELF`, and an edge with no safe non-`SELF` move reports REPOSITION.

- [ ] **Step 2: Verify the tests fail**

Run:

```bash
bash mvnw -q -Dtest=SafetyGridStateMachineTest test
```

Expected: failures showing the current `UNOWNED`-only initial filter, unrestricted later OUT step, and OUT fallback.

- [ ] **Step 3: Enforce the OUT invariant**

Filter initial OUT candidates with `!isSelfTerritory(...)`. If none remain, set REPOSITION and call `pickReposition`. In `pickOut`, require the next destination not to be `SELF` before continuing.

- [ ] **Step 4: Verify focused and full tests**

Run:

```bash
bash mvnw -q -Dtest=SafetyGridStateMachineTest test
bash mvnw test
```

Expected: all tests pass.

---

### Task 6: Replace openness with committed random ray selection

**Files:**
- Modify: `src/main/java/candidate/examples/SafetyGridStateMachine.java`
- Test: `src/test/java/candidate/examples/SafetyGridStateMachineTest.java`

- [ ] **Step 1: Add failing behavioral tests**

Cover random REPOSITION momentum, detection of non-`SELF` territory one to three straight cells away, nearest-distance selection, vertical equal-distance preference, commitment despite encountering a different edge, and inheritance of the committed direction by OUT.

- [ ] **Step 2: Verify the tests fail**

Run `bash mvnw -q -Dtest=SafetyGridStateMachineTest test`.

- [ ] **Step 3: Implement the minimal random ray heuristic**

Add one seeded `Random`. Replace all open-space ranking with random selection, except AVOID's distance ranking. During REPOSITION, preserve valid momentum; when a qualifying ray appears, choose by nearest distance, then vertical preference, then randomness. Preserve that approach until its next step reaches non-`SELF`, then let OUT inherit it.

- [ ] **Step 4: Remove obsolete scoring code and tests**

Delete `opennessScore`, `mostOpenFirst`, `isOnSideOf`, and their tests. Keep `ObservedBoard` for mirror and return-home behavior.

- [ ] **Step 5: Verify focused and full tests**

Run `bash mvnw -q -Dtest=SafetyGridStateMachineTest test` and `bash mvnw test`. Expected: all tests pass.
