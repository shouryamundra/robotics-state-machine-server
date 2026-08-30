# Safety grid edge expeditions

## Goal

Make `SafetyGridStateMachine` leave its territory without using a global open-space heuristic that can settle at an interior equilibrium. The bot should use committed random movement until non-`SELF` territory is close, then commit to a short straight approach.

## State selection

ATTACK and AVOID retain their current priority and behavior.

When the active trail is empty, select the next move once and let its destination determine the state:

- A move onto `SELF` territory is REPOSITION.
- A move onto `UNOWNED` or `OPPONENT` territory starts OUT.

There is no separate territory-edge concept or preliminary edge scan. REPOSITION begins committing to an OUT approach when non-`SELF` territory is one to three straight-line cells away, and changes to OUT only when the committed next step actually leaves `SELF`.

An active trail continues the existing OUT/ACROSS/BACK expedition state.

## REPOSITION

REPOSITION is the general state for moving through the interior of our territory. A reposition decision chooses randomly among safe moves that remain on `SELF` territory and records the winner as `repositionDirection`.

On later REPOSITION turns, continue in `repositionDirection` while that move remains safe and lands on `SELF` territory. If it becomes unavailable, choose and record a new random direction from the currently safe owned moves.

Before ordinary random movement, inspect each safe owned cardinal direction up to three cells ahead for the first `UNOWNED` or `OPPONENT` cell. If the committed direction is one of these qualifying rays, retain it. Otherwise:

1. Keep only rays with the smallest distance to non-`SELF` territory.
2. If any of those rays are vertical, keep only the vertical rays.
3. Choose randomly among the remaining rays and commit to that direction.

This is still REPOSITION while the approach crosses `SELF` territory. Clear `repositionDirection` only after its next step starts OUT. ATTACK or AVOID may temporarily interrupt REPOSITION without clearing its direction.

## Random choices

Use one deterministically seeded `Random` field so matches and tests remain reproducible.

OUT considers only safe directions whose adjacent destination is not `SELF` territory, allowing both `UNOWNED` and `OPPONENT`. If `repositionDirection` is one of those candidates, inherit it. Otherwise, prefer vertical candidates when any exist and choose randomly among the remaining candidates. Every later OUT step applies the same not-`SELF` requirement.

If no safe non-`SELF` move exists, remain on owned territory using REPOSITION rather than entering OUT through its generic fallback.

ACROSS chooses randomly among safe perpendicular directions. BACK chooses randomly among safe moves that remain on `SELF` territory. AVOID continues to maximize distance from the opponent, without an open-space tie-break.

## Implementation scope

- Replace separate REPOSITION/edge checks with one trail-free movement decision.
- Remove `TERRITORY_VISIBLE_THRESHOLD` and `VERTICAL_WEIGHT`.
- Remove `opennessScore`, `mostOpenFirst`, and `isOnSideOf`.
- Remove `isTerritoryEdge`, `onTerritoryEdge`, and `shouldReposition`.
- Add a straight-ray distance helper with a maximum distance of three.
- Retain `repositionDirection` while moving safely through owned territory.
- Add one seeded `Random` field for all random choices.
- Add focused tests for random candidate selection, committed approaches, distance priority, and vertical tie-breaking.
- Preserve the safety-grid, mirror-back, attack, avoid, across, and back behavior.

## Success criteria

- On entering REPOSITION without a nearby qualifying ray, a trail-free bot chooses a random safe owned direction.
- On subsequent REPOSITION turns, it keeps that direction while valid.
- A blocked or no-longer-owned `repositionDirection` is replaced randomly.
- A straight ray reaching non-`SELF` territory within three cells overrides unrelated random momentum and is retained until the next step leaves `SELF`.
- The nearest qualifying ray wins; equal-distance vertical rays win over horizontal rays; remaining ties are random.
- A trail-free move reports OUT exactly when its destination is non-`SELF`.
- OUT inherits its committed approach direction when possible; otherwise it prefers vertical candidates and chooses randomly.
- OUT may traverse `OPPONENT` territory but never moves onto `SELF` territory.
- With no safe non-`SELF` move, the bot enters REPOSITION instead of OUT.
- No movement decision uses remembered open-space counts.
- Existing tests and the self-play smoke test pass.
