# Safety grid state machine

A new candidate-facing example controller: a cautious, methodical bot that never risks being cut off in open space, and is meant to actually be studied as a decent strategy — unlike the two existing examples.

## Problem

`candidate/examples/` currently has `BasicStateMachine` (deliberately weak — demonstrates state-machine mechanics only, explicitly "not a template for a good strategy") and `RandomStateMachine` (a random baseline). `EnemyStateMachine` plays a genuinely decent game, but it's framework-internal, its docstring says candidates shouldn't copy it, and at ~330 lines across five states plus randomized wandering it's more than a first reference should be.

There's a gap: no example demonstrates an effective, readable strategy a candidate could actually learn from.

## Identity

A "farmer," not a fighter. It grows territory in small, deliberate rectangular bites that always stay inside a configurable box around its own head, so — by construction — it can never be caught out in the open with no safe way home. It disengages the instant the opponent is sighted, and only fights when the opponent trespasses onto its own land. This is a deliberate contrast to `EnemyStateMachine`'s more opportunistic, risk-taking style, and worth candidates comparing.

## States

Re-decided from scratch every turn, highest priority first:

1. **ATTACK** — any visible cell has `territory() == SELF` and `occupant() == OPPONENT_TRAIL`: the opponent is cutting through our land. Chase it down (cross their trail to kill them). Wins even if their agent is also visible — it's a free kill, not a risk.
2. **AVOID** — the opponent's agent is visible anywhere in the window:
   - Active trail non-empty (mid-expedition) → head for the nearest known owned territory (`territory() == SELF`), same target-seeking logic as BACK below.
   - Active trail empty (already home) → don't resume expanding. Move to whichever safe direction both lands on `territory() == SELF` and most increases distance from the opponent's position; ties broken by the same open-space/vertical-weighted scoring used for expeditions (see below).
3. **REPOSITION** — active trail empty, and at least 90% of currently visible cells have `territory() == SELF`: nothing much left to do nearby. Walk toward the opponent's half of the board (based on which side our respawn position is on), preferring moves that land on `territory() == SELF` so no trail risk is taken while relocating. Stops applying (falls through to Expedition) as soon as the 90% condition is no longer true — which happens naturally once we've walked far enough, no separate distance tracking needed.
4. **Expedition** (default) — the out/across/back cycle described next.

## The safety grid

A box of size `AWAY_GRID_SIZE` (e.g. 5×5) normally, or the larger `HOME_GRID_SIZE` (its own constant, e.g. 9×9 — not a multiplier) whenever the agent's current position is on its own half of the board (same half-check as REPOSITION uses).

Rule: every cell of the active trail, plus the head, must stay within `size / 2` (Chebyshev distance) of the *current* head position. Because this is recomputed fresh from wherever the head is right now, a monotonic out-then-across path is self-limiting on its own — there's no need for a separate "steps remaining" budget to enforce the box itself. (`stepsOut`, below, is tracked anyway, but only for the mirror-check, not to enforce the box.)

### OUT

First turn of a new expedition (trail was empty): pick a direction by open-space score, among directions that are safe (in bounds, not onto either player's trail or the opponent's agent). The score for a direction is the count of currently visible cells with `territory() == UNOWNED` that lie on that side of the agent (e.g. for EAST, visible cells with `x` greater than the agent's) — unclaimed land is what's worth exploring toward. NORTH/SOUTH scores are multiplied by `VERTICAL_WEIGHT` (> 1) before comparing, so a tie or near-tie prefers vertical movement. Record the winner as `outDirection`; `stepsOut` starts at 0.

Each subsequent turn while still in OUT: if taking one more step in `outDirection` would keep the safety-grid rule satisfied and is otherwise safe, take it and increment `stepsOut`. Otherwise, switch to ACROSS this turn without moving in `outDirection` again.

### ACROSS

On entering ACROSS: pick once between the two directions perpendicular to `outDirection` (e.g. if `outDirection` is NORTH, choose between EAST and WEST) using the same open-space scoring described above (the `VERTICAL_WEIGHT` bonus only ever affects this comparison when `outDirection` is itself EAST/WEST, i.e. when choosing between NORTH and SOUTH to go across). Record it as `acrossDirection`.

Each turn while in ACROSS, before stepping in `acrossDirection`: compute the hypothetical position after that step, then compute the position `stepsOut` steps further in the *reverse* of `outDirection` from there. If the safety grid would still hold after the step, and that hypothetical mirrored position is known to be `territory() == SELF` (checked against the current visible grid, falling back to `ObservedBoard` if it's outside the live window), take the step. Otherwise — either check failing — switch to BACK this turn without taking that step.

### BACK

Head toward the nearest known owned territory cell (`territory() == SELF`), preferring visible cells but falling back to `ObservedBoard` if none are visible, avoiding stepping onto our own trail wherever an alternative exists. This is the same target-seeking logic AVOID's retreat branch uses.

Reaching territory closes the loop (`MoveResult.CAPTURED`); the trail resets, and the next turn re-decides state from the top — ordinarily landing on a fresh OUT.

### Degenerate case (accepted, not specially handled)

If the configured grid size is so small that not even one OUT step is allowed, the bot never leaves territory and just reshuffles along its own edge instead of expanding. Not worth extra machinery to special-case in a from-scratch teaching example.

## Fields

- `Phase` enum: `ATTACK, AVOID, REPOSITION, OUT, ACROSS, BACK` — returned from `getDebugState()`.
- `Direction outDirection`, `int stepsOut`, `Direction acrossDirection` — expedition memory, meaningless outside OUT/ACROSS/BACK.
- `ObservedBoard observedBoard` — for the ACROSS mirror-check and BACK/AVOID target-seeking beyond the live window.

## Config constants

Declared at the top of the file so a candidate reading it can find and tune them immediately: `AWAY_GRID_SIZE`, `HOME_GRID_SIZE`, `TERRITORY_VISIBLE_THRESHOLD = 0.90`, and the vertical-weighting bonus applied when scoring NORTH/SOUTH over EAST/WEST.

## Placement

- New file: `candidate/examples/SafetyGridStateMachine.java`.
- Add an entry to `AvailableControllers.ALL` so it's selectable in the GUI's controller picker.
- `CANDIDATE_GUIDE.md` and `README.md`'s description of `examples/` gets a short update: this one, unlike the other two, is meant to be studied as a genuinely decent strategy.

## Tests

One smoke test, `SafetyGridStateMachineTest`, mirroring `BasicStateMachineTest`: two instances play many turns against each other via a real `GameEngine`, asserting no framework errors and that the match completes.

## Out of scope

- No general opponent-trail hunting outside our own territory (that's `EnemyStateMachine`'s job) — this bot only fights defensively.
- No handling for board/grid-size configurations so extreme the degenerate case above becomes the common case rather than an edge case.
- Not wired in as a new opponent option for `EnemyStateMachine`-style assessment matches — purely a candidate-facing reference.
