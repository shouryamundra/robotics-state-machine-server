# Candidate Guide

You write one file: `src/main/java/candidate/CandidateController.java`. Nothing
else needs to change.

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

## `GameApi`

You get one of these each turn, in `takeTurn(GameApi game)`. Call `move()`
once — anything but `INVALID` ends your turn.

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
`VisibleCell` also has its real board position attached, so you never have
to convert window coordinates to board coordinates yourself.

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
number — only `SELF_*` or `OPPONENT_*`. If more than one thing is true about
a cell, the one listed first here wins: an agent standing on a trail shows
as the agent, not the trail.

## Helpers (`territorygame.helpers`)

Small utilities so you don't have to write basic board math yourself.

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
Remembers the last thing you saw at each cell, so you can look beyond your
current window. It only remembers what you've actually seen — it won't
guess whether a cell has changed since you last looked at it.
