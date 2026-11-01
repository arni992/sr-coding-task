package com.example.scoreboard;

import java.util.List;

/**
 * Result of {@link Scoreboard#inProgressSummary()}.
 *
 * @param matches in-progress matches, ordered by total score desc, then most recently started first
 * @param errors  empty for this query, never {@code null} — kept for API consistency
 */
public record SummaryResult(List<Match> matches, List<String> errors) {
}
