 # World Cup Scoreboard

A small Java library that manages a live football World Cup scoreboard with multiple
simultaneous matches.

## Build & test

```
mvn package   # builds target/world-cup-scoreboard-1.0.0.jar
mvn test      # runs the test suite (18 tests)
```

Requirements: Java 17+ (compiled with `--release 17`), Maven. The only dependency is
JUnit 5, in test scope — the library itself has **no runtime dependencies**.

Why java 17?
Since the goal was to create library, not an app, lower versions provide broader compatibility.
Version 17 seemed like a reasonable compromise here - LTS, not too old, record classes.


Note: this repository is public. It contains no company names or branding, and
`.gitignore` excludes `*.pdf` (the task specification) and `*.jar` files.

## API

```java
Scoreboard board = new Scoreboard();

MatchResult scheduled = board.scheduleMatch("Uruguay", "Italy"); // PLANNED, 0-0
MatchResult started   = board.startMatch(scheduled.match().id()); // IN_PROGRESS
MatchResult scored    = board.updateScore(scheduled.match().id(), 6, 6);
MatchResult finished  = board.finishMatch(scheduled.match().id()); // FINISHED

SummaryResult summary = board.inProgressSummary();
// in-progress matches: total score desc, ties -> most recently started first

MatchResult detail    = board.getMatch(scheduled.match().id()); // any match, any state
```

Every returned object carries an `errors` field (`List<String>`, never `null`,
empty on success) — see "Errors as data" below.

## Match lifecycle

Three states: `PLANNED -> IN_PROGRESS -> FINISHED`.

- `scheduleMatch(home, away)` creates a new match in `PLANNED` state.
- `startMatch(id)` activates it (`IN_PROGRESS`) — only planned matches can start.
- `updateScore(id, home, away)` is only allowed while `IN_PROGRESS`.
- `finishMatch(id)` ends it; finished matches leave the summary.

## Additional operation (task item 5)

**`getMatch(MatchId)`** — full details of any match, in any state.

Chosen because the summary only shows in-progress matches, so once a match is
finished the caller has no way to inspect it. This closes that gap with the
smallest possible addition and is also useful for planned matches
("hasn't it kicked off yet?").

## Assumptions

1. **Match identity**: `scheduleMatch` returns a generated `MatchId`. Addressing
   matches by team pair would break for simultaneous rematches of the same teams
   (explicitly supported here) and for name changes.
2. **Score updates are absolute** (`setScore` semantics), not incremental `+1`.
   Absolute updates are idempotent and replay-safe, which is what live sports
   data feeds need; a goal is just `current + 1`.
3. **"Most recently started"** is measured by a monotonic sequence assigned at
   `startMatch` time, not wall-clock time — clocks collide at millisecond
   resolution and would make ordering non-deterministic. With the `PLANNED`
   state, start order and schedule order can differ; the start sequence wins.
4. **Errors as data** (per task clarification): illegal operations never throw.
   The operation is ignored, a valid result is returned, and the reason is put
   in the `errors` list. The caller decides how to handle it.
   `match` is `null` only when no match state exists to report
   (unknown id, invalid arguments).
5. **In-memory and thread-safe**: no persistence is required by the task.
   Immutable `Match` snapshots live in a `ConcurrentHashMap`; state transitions
   are applied atomically with `computeIfPresent`, so concurrent updates cannot
   corrupt state or interleave illegally.
6. Negative scores and blank team names are rejected (reported in `errors`).

## Reasoning

- **Immutable records + concurrent map** instead of mutable entities + locks:
  snapshots handed to callers can never change underneath them, so
  `inProgressSummary()` needs no defensive copying and cannot throw
  `ConcurrentModificationException`.
- **One facade (`Scoreboard`)**: the whole state machine fits in one class;
  splitting it further would be ceremony at this size.
- **`MatchId` as a record over a `long`**: type-safe signatures, readable
  (`M-42`), and the same monotonic counter serves ids and start sequences.

## Trade-offs

- **Errors-as-data vs exceptions**: no stack traces for expected misuse and
  callers can batch-check, but failures are easy to ignore if the caller
  forgets to inspect `errors`. Mitigated by documenting the contract on every
  result type.
- **Absolute score updates** reject nothing semantically (a score can go down —
  VAR corrections happen), but a buggy caller can overwrite a correct score.
  Incremental updates would need a different API and are non-idempotent.
- **No persistence**: restart loses state. The task describes a live scoreboard
  library; persistence would add a storage layer nobody asked for.
- **`PLANNED` matches are invisible in the summary** — only in-progress matches
  are listed, per the task wording. `getMatch` is the way to see them.

## Tests

| Class | Covers |
|---|---|
| `ExampleScenarioTest` | The exact scenario from the task spec — the hard acceptance gate |
| `ScoreboardTest` | Lifecycle, idempotent updates, same-team matches, all illegal operations return errors-as-data, `getMatch` |
| `OrderingTest` | Total-score desc, 3-way ties, start-time vs schedule-time tie-break, finished excluded, immutable snapshots |
| `ConcurrencyTest` | Parallel scheduling (unique ids), concurrent updates (no corruption) |
