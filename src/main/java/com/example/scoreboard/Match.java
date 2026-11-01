package com.example.scoreboard;

/**
 * Immutable snapshot of a match. Safe to hand out to callers as-is.
 *
 * @param startSeq monotonic sequence assigned when the match was started
 *                 (0 while PLANNED); used as the "most recently started" tie-break
 */
public record Match(
        MatchId id,
        String home,
        String away,
        int homeScore,
        int awayScore,
        MatchStatus status,
        long startSeq
) {

    public int totalScore() {
        return homeScore + awayScore;
    }

    Match withScore(int newHomeScore, int newAwayScore) {
        return new Match(id, home, away, newHomeScore, newAwayScore, status, startSeq);
    }

    Match withStatus(MatchStatus newStatus, long newStartSeq) {
        return new Match(id, home, away, homeScore, awayScore, newStatus, newStartSeq);
    }
}
