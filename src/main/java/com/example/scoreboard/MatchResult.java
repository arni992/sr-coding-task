package com.example.scoreboard;

import java.util.List;

/**
 * Result of a single-match operation.
 *
 * <p>"Errors as data": illegal operations never throw. The operation is ignored,
 * the result is still valid, and the reason is reported in {@link #errors()}.
 *
 * @param match   the match after the operation, or {@code null} when the operation failed
 *                (e.g. unknown id) and no match state exists to report
 * @param errors  human-readable problems; empty on success, never {@code null}
 */
public record MatchResult(Match match, List<String> errors) {

    static MatchResult ok(Match match) {
        return new MatchResult(match, List.of());
    }

    static MatchResult error(Match match, String message) {
        return new MatchResult(match, List.of(message));
    }
}
