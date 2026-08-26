# Territory Capture

A local two-player territory-capture game, built as a Java framework for an
interview assessment: the framework owns the board, rules, turn execution,
and GUI; a candidate implements one file — an `AgentController` — to play.

Two agents move around a grid, laying trails outside their own territory
and closing them to capture the enclosed area (and any opponent territory
inside it). Crossing your own trail kills you; crossing an opponent's trail
kills them. Whoever holds more territory when both players run out of
turns wins.

## Requirements

- Java 21
- Maven

## Running it

```
mvn test        # run the test suite
mvn exec:java    # launch the GUI
```

In the GUI, pick which controller occupies each player slot (Example
Agent, Provided Bot, Random Agent, or the candidate's own controller), then
use Start / Pause / Step / Reset to run a match.

## Candidate assessment

See `CANDIDATE_GUIDE.md` for the assessment task: the file to edit, the
rules from a player's perspective, and the API reference.

## Tips

Ideas worth thinking about — none of these are solved for you in the
example controllers:

- **Avoid your own trail.** `MovementUtils.isValidMove` only checks board
  bounds and the opponent's agent; stepping on your own trail still
  passes it, but it kills you.
- **Watch for the opponent's trail too** — crossing it kills *them*, and
  their agent's position hints at where their trail might be.
- **Aggression vs. caution.** Compare `getOwnedTerritoryCellCount()` to
  `getOpponentTerritoryCellCount()` — ahead, maybe hunt for a kill;
  behind, maybe you're under pressure and should play safer.
- **Getting home efficiently.** `getRespawnPosition()` isn't necessarily
  your nearest owned cell once you've captured territory elsewhere —
  scanning `getVisibleGrid()` for the nearest `SELF_TERRITORY` cell can
  do better.
- **Trail length is a trade-off.** Longer trails claim more area on
  capture but leave you exposed for longer.
- **Remember what you've seen.** `ObservedBoard` builds a picture beyond
  your current visible window — useful for planning ahead.
- **Remaining turns matter.** Early vs. late game might call for
  different behavior — `getRemainingTurns()` tells you where you stand.

## Project structure

```
src/main/resources/
  game-config.properties     board size, visibility, turn count, respawn
                              positions, starting-territory size

src/main/java/territorygame/
  api/          Candidate-facing types: GameApi, AgentController,
                GridPosition, VisibleCell, CellViewType, Direction,
                MoveResult. Nothing outside this package is ever handed to
                candidate code.

  domain/       Authoritative game state: PlayerId, GameConfig, Agent,
                Player, Board, BoardCell, GameState. Not exposed to
                candidates or the GUI.

  rules/        The actual rules, isolated from turn management:
                MoveResolver (one move's resolution order), TerritoryResolver
                (flood-fill capture), RespawnService (death/reset).

  visibility/   VisibilityService — builds a candidate's visible-cell
                window and translates internal player identities to
                SELF_*/OPPONENT_* types.

  engine/       Match orchestration: GameEngine (lifecycle, Start/Pause/
                Step/Reset), TurnManager (one turn, controller retry-on-
                invalid), GameApiImpl (per-player facade over GameApi),
                GameSnapshot/GameObserver (the read-only view the GUI
                renders from).

  helpers/      Provided utilities candidates may use: ObservedBoard
                (remembers the latest observed value per cell),
                MovementUtils (position/bounds arithmetic, mechanical move
                validation, Manhattan distance).

  controller/   Framework-internal AgentController implementations not
                meant as examples to copy: ProvidedBotController (the
                standard assessment opponent) and AvailableControllers
                (the static registry the GUI's controller picker reads
                from — Example Agent, Provided Bot, Random Agent,
                Candidate Controller).

  gui/          Swing viewer: GameWindow (controls, status, wiring) and
                BoardPanel (custom-painted board rendering). No game-rule
                logic lives here.

  Main.java     Entry point — launches the GUI on the Swing event thread.

src/main/java/candidate/
  CandidateController.java   the file to edit for the assessment.

  examples/                  Read-only reference controllers: ExampleAgentController
                              (a deliberately weak state-machine example) and
                              RandomAgentController (the simplest possible baseline).
                              Not templates for a good strategy.

src/test/java/...            mirrors the main package layout.
```

## Known issues / TODO

Found via a multi-pass code review after the initial implementation. Not
yet fixed — listed here so they're tracked instead of silent.

**Correctness bugs:**

- **Two agents can end up on the same cell.** In `MoveResolver.resolve`
  (`rules/MoveResolver.java`), when a move kills the opponent by crossing
  their trail, `respawnService.respawn(state, opponent)` runs *before*
  `moverAgent.setPosition(destination)`. `RespawnService.choosePosition`
  checks whether the opponent's fixed respawn point is occupied using the
  *mover's pre-move position*, not the destination it's about to move to.
  If the mover is walking onto the opponent's respawn point itself, the
  occupancy check passes trivially, the opponent respawns there, and the
  mover then moves onto the same cell — both agents end up stacked on one
  cell, which permanently breaks the `AGENT > TRAIL > TERRITORY > FREE`
  visibility precedence for that cell (the opponent becomes invisible to
  both players' `getVisibleGrid()`). Fix: compute the mover's destination
  before running the opponent's respawn, and have `RespawnService` check
  against it too.
- **Exceptions inside `GameEngine`'s background tasks vanish silently.**
  `start()`/`step()`/`reset()` all submit to a single-thread
  `ExecutorService` (`engine/GameEngine.java`) via `submit(Runnable)`;
  nothing ever calls `.get()` on the returned `Future`, so any uncaught
  exception (a buggy controller throwing, or the `RespawnService` fallback
  below) kills the in-flight task with zero observable effect — the match
  just stops updating, `running` stays `true` forever, and Start/Pause/
  Step/Reset all appear to do nothing afterward.
- **`RespawnService.choosePosition`'s fallback can throw.** If
  `starting.territorySize=1` and the opponent happens to be standing on
  that single cell, the fallback search's `.orElseThrow()`
  (`rules/RespawnService.java`) throws `IllegalStateException` — which,
  combined with the point above, freezes the engine with no diagnostic.
- **The two-player assumption is declared but never enforced.**
  `GameState`'s javadoc states it assumes exactly two players, but neither
  `GameConfig` nor `GameEngine` validates `respawn.count`.
  `respawn.count=1` crashes on the first move inside
  `GameState.getOpponent`; `respawn.count=3` doesn't crash at all — it
  silently treats one player as invisible to every rule (collision,
  trail-kill, respawn, visibility).
- **`GameConfig` only validates that properties parse, not their values.**
  Negative/zero board dimensions, out-of-bounds respawn positions,
  overlapping starting territories, and even `visibility.windowSize`
  values are all accepted silently or fail later with a confusing
  exception several layers away from the actual misconfiguration.
- **`TurnManager`'s controller-retry loop has no cap.** A cornered agent
  or a controller that never calls a successful `move()` spins the retry
  loop forever, wedging the single-thread executor — since every engine
  command shares that one executor, this freezes Start/Pause/Step/Reset
  entirely, not just that one match.
- **`GameSnapshot.cells` is a raw array, not defensively copied.** Despite
  the class's javadoc claiming it's "immutable, fully copied, safe to hand
  across threads," a Java record never defensively copies array-typed
  components — a second observer holding the same snapshot instance could
  mutate the shared array. The same issue means the record's
  auto-generated `equals()`/`hashCode()` use array reference identity, so
  two snapshots with identical board state are never considered equal.

**Code quality (duplication):**

- `RespawnService` reimplements `MovementUtils.manhattanDistance` as a
  private method instead of calling it.
- Board-bounds checking exists as three independent implementations:
  `Board.isWithinBounds`, `MovementUtils.isWithinBoard`, and a private
  copy in `ObservedBoard`.
- `TerritoryResolver.cardinalNeighbors` hand-rolls neighbor arithmetic
  that duplicates `MovementUtils.nextPosition` + `isWithinBoard`.
- The "scan `VisibleCell[][]` for a matching position" loop is
  independently written four times (`MovementUtils.isValidMove`,
  `ProvidedBotController.typeAt`, `ProvidedBotController.nearestSelfTerritory`,
  `ObservedBoard.update`).
- The two example controllers duplicate the "collect mechanically-valid
  directions" loop instead of sharing a helper.
- A uniform-random-direction picker is duplicated three times across
  `ProvidedBotController`, `ExampleAgentController`, and
  `RandomAgentController`.
- `ProvidedBotController.chooseExpandingDirection`/`chooseReturningDirection`
  are copy-pasted, differing only in which comparator is applied.

**Efficiency:**

- `Board.territoryCount`/`territoryOf` are full O(width×height) scans on
  every call, called at least twice per turn for the GUI snapshot, despite
  `Board` already tracking ownership incrementally.
- `GameEngine.buildSnapshot` rebuilds the entire cell grid from scratch
  every turn even though a typical move changes 1-2 cells.
- `TerritoryResolver`'s flood fill allocates a new list and up to four
  `GridPosition` objects per visited cell.
- `MovementUtils.isValidMove` scans the whole visible grid to guard a
  case its own comment admits is unreachable given the board-bounds check
  already above it.
- `RespawnService.clearAllTerritoryOf` does a redundant full-board scan
  and `Set` allocation on every respawn.
