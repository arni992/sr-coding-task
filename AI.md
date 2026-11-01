# AI.md — How AI was used

## Summary

An AI coding agent (OpenCode, Qwen model) was used in two phases:

1. **Requirements analysis** — the agent extracted the task PDF (`pdftotext`),
   produced a requirements list, ambiguity/assumption inventory, an
   implementation plan and a test plan. No code was written in this phase.
2. **Implementation** — the agent scaffolded the Maven project, wrote the
   library and tests, ran `mvn test`, and fixed two failing tests (both were
   bugs in the tests, not in the library — see "Test debugging" below).

Human decisions that drove the design: Java 17/Maven, the 3-state match model
(adding `PLANNED`), the errors-as-data approach (instead of exceptions),
`.gitignore` rules (`*.pdf`, `*.jar`), public-repo constraint (no company
names), and the instruction to avoid git operations.

Artifacts that guided the implementation: the requirements analysis below
(section "Assistant response #1") — its file structure, data model and test
plan were adopted almost verbatim, with the errors/exceptions section replaced
by the errors-as-data clarification.

## Prompt history

### User prompt #1

> Read the task specification PDF (filename anonymized here — it contains the company name) in this repository.
>
> 1. Extract every functional and non-functional requirement.
> 2. Identify ambiguities, missing details, assumptions, and edge cases.
> 3. Propose a concise implementation plan: architecture, modules/files, data model if relevant, external dependencies, and testing strategy.
> 4. Do not write implementation code yet.
> 5. Treat this directory as project root, don't make changes outside of it
>
> Return:
> - A numbered list of requirements, each with its source page/section in the PDF
> - Open questions or assumptions, marked clearly
> - A proposed file/folder structure
> - A test plan with concrete acceptance criteria

### Assistant response #1 (full analysis)

Extracted the full text (4 physical pages; the only images are the cover art
and logo, so nothing is hidden). Note: the PDF's printed footers say
"Page | 3–5" while the file has 4 pages — citations use physical PDF page +
section name.

#### 1. Requirements

Functional:

| # | Requirement | Source |
|---|---|---|
| F1 | Implement a **library** (not an app/CLI) managing a Live Football World Cup Scoreboard | p.1 "Coding exercise", p.2 "What we expect" |
| F2 | Support **multiple simultaneous matches** | p.2 "Requirements" |
| F3 | **Start a new match** | p.3 "Core Operations" #1 |
| F4 | **Update the score** | p.3 "Core Operations" #2 |
| F5 | **Finish a match** | p.3 "Core Operations" #3 |
| F6 | **Get a summary of matches in progress**, ordered by total score (home+away) descending | p.3 "Core Operations" #4 |
| F7 | Ties in total score → **most recently started match first** | p.3 "Core Operations" #4 |
| F8 | The worked example (Mexico/Canada, Spain/Brazil, Germany/France, Uruguay/Italy, Argentina/Australia) must produce the exact stated ordering — this is the spec's acceptance test and confirms F6/F7 semantics (Uruguay 12 before Spain 12; Argentina 4 before Germany 4) | p.3 "Example Scenario" |
| F9 | Add **exactly one** additional operation of your choice, documented in README, introduced in a **distinct git commit** | p.3 "Core Operations" #5 |

Non-functional / deliverables:

| # | Requirement | Source |
|---|---|---|
| N1 | Java package in a **Maven project** | p.2 "What we expect" |
| N2 | Delivered as a **git repository**, at code-review quality, following your own engineering standards | p.2 "What we expect" |
| N3 | **README.md** documenting assumptions, reasoning, trade-offs | p.2, p.4 "Deliverables" |
| N4 | **AI.md**: summary of AI usage, prompt history, guiding artifacts (AI use is *required* by the task) | p.2 "What we expect" |
| N5 | The requirements "intentionally include open questions" — decisions must be **made and documented** | p.2 "Requirements" |
| N6 | Repository contains: library implementation + README.md + AI.md | p.4 "Deliverables" |

#### 2. Ambiguities, assumptions, edge cases

Open questions (unspecified in the PDF):

1. **Match identity** — no match ID in the spec. How do you reference a match
   to update/finish it? Team-pair as key? Returned ID?
2. **Score update semantics** — absolute new score, or incremental "+1 goal"?
   Can a score decrease (VAR correction)? Negative scores?
3. **Illegal operations** — update/finish a match that doesn't exist or is
   already finished: throw, ignore, or return a flag?
4. **Duplicate matches** — can two matches with the same teams run
   simultaneously (rematch, or two youth games)? Can a finished match be
   restarted?
5. **"Most recently started"** — wall-clock timestamps can collide at the same
   millisecond. Tie-break needs a monotonic sequence, not `Instant`.
6. **Concurrency** — "live scoreboard" implies concurrent feed updates, but
   thread-safety is not stated.
7. **Persistence** — not mentioned; assume in-memory.
8. **Does "finish" remove the match from the summary?** — implied yes
   ("matches in progress"), but can finished matches be queried at all?
9. **Java version, test framework, build config** — unspecified.
10. **What counts as "one operation"** for F9 — one public API method presumably.

Assumptions adopted (and documented in README):

- A1: `startMatch(home, away)` returns a generated `MatchId`; updates/finish
  address the match by ID. (Team-pair keys break on duplicates and renames.)
- A2: Score update is **absolute** (`setScore(home, away)`) — idempotent,
  replay-safe, matches how sports data feeds work; a goal is just
  `setScore(current+1)`.
- A3: Illegal operations (unknown ID, finished match, negative score) throw
  typed exceptions — fail fast at the library boundary. *(superseded — see
  user prompt #2: replaced with errors-as-data)*
- A4: Start order tracked with a monotonic `AtomicLong` sequence, not clock
  time → deterministic tie-break, testable.
- A5: Thread-safe in-memory store (`ConcurrentHashMap` + immutable snapshots);
  no persistence.
- A6: Java 17+, JUnit 5, no runtime dependencies.
- A7: F9 feature: **`getMatch(MatchId)` — full details of any match, in
  progress or finished** (smallest useful operation; enables "what just
  finished?" use case). Alternatives considered: `getFinishedMatches()` or a
  group/league table.

Edge cases to pin down in tests: empty summary; summary excludes finished
matches; double-finish; update after finish; unknown ID; same-team matches;
summary list is a defensive copy; score 0–0 on start; ties at equal totals
with three+ matches.

#### 3. Implementation plan

Architecture: single-layer in-memory library, one public facade, immutable
domain objects, no frameworks.

```
sr-coding-task/
├── pom.xml                      # Maven, Java 17, JUnit 5 test-scope only
├── README.md                    # assumptions, reasoning, trade-offs, feature write-up
├── AI.md                        # AI usage summary + prompt history
├── .gitignore
└── src/
    ├── main/java/com/example/scoreboard/
    │   ├── Scoreboard.java      # public API facade
    │   ├── Match.java           # immutable entity
    │   ├── MatchId.java         # record wrapping a monotonic long
    │   ├── MatchSummary.java    # DTO for summary  (dropped in implementation:
    │   │                        #   Match itself is already immutable, so the
    │   │                        #   DTO was redundant)
    │   └── exceptions/          # (dropped: superseded by errors-as-data)
    └── test/java/com/example/scoreboard/
        ├── ExampleScenarioTest.java   # F8 verbatim — the spec acceptance test
        ├── ScoreboardTest.java        # lifecycle + edge cases
        ├── OrderingTest.java          # F6/F7 tie-break rules
        └── ConcurrencyTest.java       # parallel updates, no lost writes
```

Data model: `Match { MatchId id; String home, away; int homeScore, awayScore;
long startSeq; MatchStatus status }`. Summary comparator: total desc, then
start sequence desc.

Dependencies: JUnit 5 (test scope only). Nothing at runtime.

#### 4. Test plan / acceptance criteria

| Test | Criterion |
|---|---|
| Example scenario (F8) | Start the 5 matches in the given order, set the given scores, `inProgressSummary()` equals exactly: Uruguay 6–6, Spain 10–2, Mexico 0–5, Argentina 3–1, Germany 2–2 |
| Start | Returns unique ID; match appears in summary at 0–0 |
| Update | Summary reflects new score; absolute set is idempotent |
| Finish | Match disappears from summary; second finish reports error; update after finish reports error |
| Unknown ID | Update/finish with unknown ID reports error in result |
| Invalid score | Negative score reports error |
| Ordering | Equal totals → later start first (3-way tie); empty store → empty list |
| Duplicate teams | Two simultaneous Germany–France matches tracked independently |
| Encapsulation | Mutating the returned summary does not affect scoreboard state |
| Concurrency | N threads × M updates → no lost updates, reads never throw |
| Feature (F9) | `getMatch(id)` returns finished match details; unknown ID reports error |

### User prompt #2

> Your assumptions are generally valid but with clarifications:
> - Java 17 and JUnit 5 are ok, use maven, "mvn package" script should build the .jar file and "mvn test" run the tests. Put this information in readme file
> - put this conversation history in ai.md file
> - modify .gitignore and put a rule to ignore everything with .pdf extension (code cannot contain company name, code will be in public repo)
> - also add jar files to gitignore
> - make match status 3-state - add "planned" status
> - Change approach of errors and illegal operations:
>   Assume "error as data" approach. Add "errors" field to every returned object (List<String> type, never null, just empty for successful verification).
>   Even if someone executes illegal/not allowed operation - return valid result, ignore operation, but put information about that in errors list. Let user decide how to handle those.
> - do not try to make any git related operations.
>
> Proceed with implementation.

### Assistant response #2 (implementation notes)

Design changes forced by the clarifications:

- **3-state lifecycle**: `scheduleMatch(home, away)` creates a `PLANNED`
  match; `startMatch(id)` activates it. The spec's "start a new match" maps
  to `scheduleMatch` (creating a new match); the start sequence used for the
  tie-break is assigned at `startMatch`, not at scheduling — with a planned
  state the two orders can differ, and the spec says "most recently
  *started*".
- **Errors as data**: no exception classes at all. `MatchResult` and
  `SummaryResult` records each carry `List<String> errors` (never null).
  Illegal transitions are applied via `ConcurrentHashMap.computeIfPresent`:
  the predicate rejects them, the match is left untouched, and the reason is
  returned. `match` is `null` only when there is no state to report
  (unknown id, blank team names, negative scores).
- **No company name**: package `com.example.scoreboard`, artifact
  `world-cup-scoreboard`.
- **No git operations** were performed; the distinct-commit requirement from
  the PDF is left to the human submitting the repo.

Test debugging (what the AI got wrong initially):

1. `OrderingTest.tieBreakUsesStartTimeNotScheduleTime` failed — the *test's*
   expectation was inverted; the library correctly ordered the tie by
   "most recently started first". Test fixed, library untouched.
2. `ConcurrencyTest.parallelSchedulingProducesUniqueMatches` found 0 matches
   — the test scheduled matches but never started them, and the summary only
   lists in-progress matches. Test fixed.

Final state: `mvn test` → 18 tests, 0 failures; `mvn package` →
`target/world-cup-scoreboard-1.0.0.jar`.
