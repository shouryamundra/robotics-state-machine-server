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

- Java 21 (JDK)

## Getting started

### 1. Install Java 21

Check first — you might already have it:

```
java -version
```

If that's missing or shows an older version:

- **Windows:** Get the x64 Installer from [oracle.net](https://www.oracle.com/java/technologies/downloads/#jdk21-windows)
- **macOS:** `brew install openjdk@21` (if you don't have homebrew, [install here](https://brew.sh/))
- **Linux / WSL:** `sudo apt install openjdk-21-jdk` (Debian/Ubuntu — use
  your distro's package manager otherwise)

### 2. Clone the repository

```
git clone https://github.com/shouryamundra/robotics-state-machine-server.git
cd robotics-state-machine-server
```

### 3. Compile and run

```
bash ./mvnw compile exec:java     # macOS / Linux / WSL
mvnw.cmd compile exec:java   # Windows (cmd or PowerShell)
```

In the GUI, pick which controller occupies each player slot (Basic State
Machine, Enemy State Machine, Random State Machine, or the candidate's own
controller), then use Start / Pause / Step / Reset to run a match.

## Candidate assessment

See `CANDIDATE_GUIDE.md` for the assessment task: the file to edit, the
rules from a player's perspective, the API reference, and some tips.

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
                meant as examples to copy: EnemyStateMachine (the
                standard assessment opponent) and AvailableControllers
                (the static registry the GUI's controller picker reads
                from — Basic State Machine, Enemy State Machine, Random
                State Machine, Candidate Controller).

  gui/          Swing viewer: GameWindow (controls, status, wiring) and
                BoardPanel (custom-painted board rendering). No game-rule
                logic lives here.

  Main.java     Entry point — launches the GUI on the Swing event thread.

src/main/java/candidate/
  CandidateController.java   the file to edit for the assessment.

  examples/                  Read-only reference controllers: BasicStateMachine
                              (a deliberately weak state-machine example) and
                              RandomStateMachine (the simplest possible baseline).
                              Not templates for a good strategy.

src/test/java/...            mirrors the main package layout.
```

