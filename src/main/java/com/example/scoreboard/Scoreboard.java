package com.example.scoreboard;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory scoreboard supporting multiple simultaneous matches.
 *
 * <p>Design decisions (see README.md):
 * <ul>
 *   <li>Matches are created as {@link MatchStatus#PLANNED} and activated by
 *       {@link #startMatch(MatchId)}; "most recently started" is measured by the
 *       sequence assigned at start time, not wall-clock time (clock collisions).</li>
 *   <li>Score updates are absolute ({@code setScore} semantics) — idempotent and
 *       replay-safe for data feeds; a goal is simply current + 1.</li>
 *   <li>Errors as data: illegal operations are ignored and reported in the
 *       {@code errors} list of the returned result instead of throwing.</li>
 *   <li>Thread-safe: immutable {@link Match} snapshots in a {@link ConcurrentHashMap},
 *       transitions applied atomically via {@code computeIfPresent}.</li>
 * </ul>
 */
public class Scoreboard {

    private final ConcurrentMap<MatchId, Match> matches = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Creates a new match in {@link MatchStatus#PLANNED} state.
     * The same two teams may play several simultaneous matches.
     */
    public MatchResult scheduleMatch(String home, String away) {
        if (isBlank(home) || isBlank(away)) {
            return MatchResult.error(null, "Team names must not be blank");
        }
        Match match = new Match(new MatchId(sequence.incrementAndGet()),
                home.trim(), away.trim(), 0, 0, MatchStatus.PLANNED, 0);
        matches.put(match.id(), match);
        return MatchResult.ok(match);
    }

    /** Transitions a PLANNED match to IN_PROGRESS. */
    public MatchResult startMatch(MatchId id) {
        return transition(id, "start",
                m -> m.status() == MatchStatus.PLANNED,
                m -> m.withStatus(MatchStatus.IN_PROGRESS, sequence.incrementAndGet()));
    }

    /**
     * Sets the current score of an in-progress match. Absolute values, negative scores rejected.
     */
    public MatchResult updateScore(MatchId id, int homeScore, int awayScore) {
        if (homeScore < 0 || awayScore < 0) {
            return MatchResult.error(null, "Scores must not be negative: " + homeScore + "-" + awayScore);
        }
        return transition(id, "update score of",
                m -> m.status() == MatchStatus.IN_PROGRESS,
                m -> m.withScore(homeScore, awayScore));
    }

    /** Transitions an IN_PROGRESS match to FINISHED; it disappears from the summary. */
    public MatchResult finishMatch(MatchId id) {
        return transition(id, "finish",
                m -> m.status() == MatchStatus.IN_PROGRESS,
                m -> m.withStatus(MatchStatus.FINISHED, m.startSeq()));
    }

    /**
     * The one additional operation of the task: full details of any match,
     * in any state. Chosen because it answers "what happened to that match?"
     * once it leaves the in-progress summary.
     */
    public MatchResult getMatch(MatchId id) {
        Match match = matches.get(id);
        return match == null
                ? MatchResult.error(null, "Match not found: " + id)
                : MatchResult.ok(match);
    }

    /** In-progress matches, total score desc, ties broken by most recently started first. */
    public SummaryResult inProgressSummary() {
        List<Match> inProgress = matches.values().stream()
                .filter(m -> m.status() == MatchStatus.IN_PROGRESS)
                .sorted(Comparator.comparingInt(Match::totalScore).reversed()
                        .thenComparing(Comparator.comparingLong(Match::startSeq).reversed()))
                .toList();
        return new SummaryResult(inProgress, List.of());
    }

    /**
     * Applies {@code change} atomically when {@code allowed} accepts the current state;
     * otherwise the match is left untouched and the reason is returned as data.
     */
    private MatchResult transition(MatchId id, String action,
                                   java.util.function.Predicate<Match> allowed,
                                   java.util.function.UnaryOperator<Match> change) {
        String[] rejection = new String[1];
        Match result = matches.computeIfPresent(id, (key, current) -> {
            if (!allowed.test(current)) {
                rejection[0] = "Cannot " + action + " match " + id + " in state " + current.status();
                return current;
            }
            return change.apply(current);
        });
        if (result == null) {
            return MatchResult.error(null, "Match not found: " + id);
        }
        return rejection[0] == null ? MatchResult.ok(result) : MatchResult.error(result, rejection[0]);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
