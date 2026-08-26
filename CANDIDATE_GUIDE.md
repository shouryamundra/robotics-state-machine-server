# Candidate Guide

You write `src/main/java/candidate/CandidateController.java` — that's the
one file the framework looks for. Want to split your logic across more
classes? Go ahead: add as many files as you like inside
`src/main/java/candidate/`. Nothing outside that folder needs to change.

## The rules

- You move one step at a time: NORTH, SOUTH, EAST, or WEST.
- Step outside your own territory → you start (or extend) a trail.
- Step back onto territory you own → your trail closes and becomes territory.
  Anything it surrounds becomes yours too, including enemy territory.
- Step on your own trail → you die.
- Step on the enemy's trail → they die, you're fine.
- Dying sends you back to your starting zone. You keep playing after that.
- The match ends when both players run out of turns. Most territory wins.
- A move can be `INVALID` (off the board, or into the opponent's current
  spot). Nothing happens, and you get asked to move again.

## `AgentController`

This is the interface `CandidateController` implements. 

### takeTurn(game)
```
void takeTurn(GameApi game)
```
Put your decision-making here. You never call this method yourself — the
framework calls it for you, whenever it's your turn.

`game` (a `GameApi`, see below) is your window into the current turn: where
you are, what you can see, how the match stands. Read what you need from
it, decide on a direction, then call `game.move(direction)` **exactly
once** — that call is your whole turn. `takeTurn` doesn't return anything;
`move()` is how you actually act.

If the direction you pick comes back `INVALID`, nothing happens and
`takeTurn` is called again right away so you can try something else —
still the same turn.

### getDebugState()
```
default String getDebugState() { return null; }
```
Entirely optional. Return a short label for whatever state you think
you're in (an enum's name works well), and the GUI shows it next to your
player card — handy for watching a match and seeing what your controller
is "thinking." No effect on gameplay either way.

## `GameApi`

Your one-turn snapshot of the match. Read what you need, then call
`game.move(direction)` once.

### getAgentPosition()
```
GridPosition getAgentPosition()
```
Where you are right now.

### getRespawnPosition()
```
GridPosition getRespawnPosition()
```
Where you reappear after dying.

### getOwnedTerritoryCellCount()
```
int getOwnedTerritoryCellCount()
```
How many cells you currently own.

### getOpponentTerritoryCellCount()
```
int getOpponentTerritoryCellCount()
```
How many cells the opponent currently owns.

### getRemainingTurns()
```
int getRemainingTurns()
```
How many turns you have left.

### getActiveTrail()
```
List<GridPosition> getActiveTrail()
```
Your current trail, oldest cell first. Empty if you're standing on your own
territory right now.

### getVisibleGrid()
```
VisibleCell[][] getVisibleGrid()
```
The cells you can currently see, centered on you. Index it `[y][x]`. Each
`VisibleCell` carries its real board position too, so you never have to
convert window coordinates to board coordinates yourself.

### getBoardWidth() / getBoardHeight()
```
int getBoardWidth()
int getBoardHeight()
```
The size of the whole board (not just what you can see).

### move(direction)
```
MoveResult move(Direction direction)
```
Try to move one step. Returns what happened.

## Types

### GridPosition
```
record GridPosition(int x, int y)
```
A cell on the board. `(0, 0)` is the top-left corner.

### VisibleCell
```
record VisibleCell(GridPosition position, CellViewType type)
```
One cell you can see, and what's in it.

### Direction
```
enum Direction { NORTH, SOUTH, EAST, WEST }
```

### MoveResult
```
enum MoveResult { MOVED, CAPTURED, DIED, INVALID }
```
What `move()` just did: `MOVED` (normal step), `CAPTURED` (your trail
closed), `DIED` (you hit a trail), or `INVALID` (nothing happened).

### CellViewType
```
enum CellViewType {
    FREE, SELF_TERRITORY, OPPONENT_TERRITORY,
    SELF_TRAIL, OPPONENT_TRAIL, SELF_AGENT, OPPONENT_AGENT
}
```
What's in a cell you can see. You'll never see the opponent's real player
number — only `SELF_*` or `OPPONENT_*`. If more than one thing is true
about a cell, the one listed first here wins: an agent standing on a trail
shows as the agent, not the trail.

## Helpers (`territorygame.helpers`)

Basic board math you shouldn't have to write yourself — free to use from
`CandidateController`.

### MovementUtils.nextPosition
```
GridPosition nextPosition(GridPosition position, Direction direction)
```
The cell one step away in a direction.

### MovementUtils.isWithinBoard
```
boolean isWithinBoard(GridPosition position, int width, int height)
```
Is this cell actually on the board?

### MovementUtils.isValidMove
```
boolean isValidMove(GameApi game, Direction direction)
```
Would this move be on the board and not walk into the opponent's agent?
**This does not check trails.** Walking into your own trail (or theirs)
still counts as "valid" here — that part is on you.

### MovementUtils.validDirections
```
List<Direction> validDirections(GameApi game)
```
All directions that pass `isValidMove` right now.

### MovementUtils.manhattanDistance
```
int manhattanDistance(GridPosition a, GridPosition b)
```
Grid distance between two cells (no diagonals).

### MovementUtils.findCell
```
Optional<VisibleCell> findCell(VisibleCell[][] visibleGrid, GridPosition position)
```
Look up one cell in a visible grid by its board position.

### MovementUtils.randomDirection
```
Direction randomDirection(Random random)
```
Picks one of the four directions at random.

### ObservedBoard
```
ObservedBoard(int width, int height)
void update(VisibleCell[][] visibleGrid)   // call this each turn
Optional<CellViewType> get(GridPosition position)
boolean hasObserved(GridPosition position)
void clear()
```
Remembers the last thing you saw at each cell, so you can reason about
parts of the board outside your current window. It only remembers what
you've actually seen — it won't guess whether a cell has changed since.

## Tips

Nothing below is solved for you in the example controllers — worth
thinking about once the basics are working:

- **Avoid your own trail.** `isValidMove` only checks board bounds and the
  opponent's agent; stepping on your own trail still passes it, but it
  kills you.
- **Watch for the opponent's trail too** — crossing it kills *them*, and
  their agent's position hints at where their trail might be.
- **Aggression vs. caution.** Compare `getOwnedTerritoryCellCount()` to
  `getOpponentTerritoryCellCount()` — ahead, maybe hunt for a kill; behind,
  maybe play safer.
- **Getting home efficiently.** `getRespawnPosition()` isn't necessarily
  your nearest owned cell once you've captured territory elsewhere —
  scanning `getVisibleGrid()` for the nearest `SELF_TERRITORY` cell can do
  better.
- **Trail length is a trade-off.** Longer trails claim more area on
  capture but leave you exposed for longer.
- **Remember what you've seen.** `ObservedBoard` builds a picture beyond
  your current visible window — useful for planning ahead.
- **Remaining turns matter.** Early vs. late game might call for
  different behavior — `getRemainingTurns()` tells you where you stand.
