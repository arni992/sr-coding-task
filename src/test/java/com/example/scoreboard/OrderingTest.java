package com.example.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordering rules: total score desc, ties broken by most recently started first.
 */
class OrderingTest {

    private MatchId startWithScore(Scoreboard board, String home, String away, int h, int a) {
        MatchId id = board.scheduleMatch(home, away).match().id();
        board.startMatch(id);
        board.updateScore(id, h, a);
        return id;
    }

    @Test
    void emptyBoardGivesEmptySummary() {
        SummaryResult summary = new Scoreboard().inProgressSummary();
        assertTrue(summary.matches().isEmpty());
        assertTrue(summary.errors().isEmpty());
    }

    @Test
    void threeWayTieIsOrderedByMostRecentlyStartedFirst() {
        Scoreboard board = new Scoreboard();
        startWithScore(board, "TeamA", "TeamB", 1, 1); // total 2, oldest
        startWithScore(board, "TeamC", "TeamD", 2, 0); // total 2
        startWithScore(board, "TeamE", "TeamF", 0, 2); // total 2, newest

        List<String> order = board.inProgressSummary().matches().stream()
                .map(Match::home).toList();
        assertEquals(List.of("TeamE", "TeamC", "TeamA"), order);
    }

    @Test
    void tieBreakUsesStartTimeNotScheduleTime() {
        Scoreboard board = new Scoreboard();
        MatchId first = board.scheduleMatch("Early", "Scheduled").match().id();
        MatchId second = board.scheduleMatch("Late", "Scheduled").match().id();

        // second match scheduled later but started first -> "most recently started first" puts Early ahead
        board.startMatch(second);
        board.startMatch(first);
        board.updateScore(first, 1, 1);
        board.updateScore(second, 2, 0);

        List<String> order = board.inProgressSummary().matches().stream()
                .map(Match::home).toList();
        assertEquals(List.of("Early", "Late"), order);
    }

    @Test
    void finishedMatchesAreExcluded() {
        Scoreboard board = new Scoreboard();
        MatchId done = startWithScore(board, "Finished", "Match", 9, 9);
        startWithScore(board, "Ongoing", "Match", 1, 0);
        board.finishMatch(done);

        List<String> order = board.inProgressSummary().matches().stream()
                .map(Match::home).toList();
        assertEquals(List.of("Ongoing"), order);
    }

    @Test
    void summaryIsImmutableSnapshot() {
        Scoreboard board = new Scoreboard();
        startWithScore(board, "Home", "Away", 1, 0);
        List<Match> snapshot = board.inProgressSummary().matches();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(0));

        board.updateScore(snapshot.get(0).id(), 5, 5);
        assertEquals(1, snapshot.get(0).homeScore()); // old snapshot unaffected
    }
}
