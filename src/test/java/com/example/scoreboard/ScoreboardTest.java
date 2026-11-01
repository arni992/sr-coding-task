package com.example.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Match lifecycle and the "errors as data" contract:
 * illegal operations are ignored, the result stays valid, errors explain why.
 */
class ScoreboardTest {

    private Match startedMatch(Scoreboard board, String home, String away) {
        Match m = board.scheduleMatch(home, away).match();
        return board.startMatch(m.id()).match();
    }

    @Test
    void scheduledMatchIsPlannedAndNotInSummary() {
        Scoreboard board = new Scoreboard();
        MatchResult result = board.scheduleMatch("Poland", "Japan");

        assertTrue(result.errors().isEmpty());
        assertEquals(MatchStatus.PLANNED, result.match().status());
        assertEquals(0, result.match().homeScore());
        assertEquals(0, result.match().awayScore());
        assertTrue(board.inProgressSummary().matches().isEmpty());
    }

    @Test
    void fullLifecycle() {
        Scoreboard board = new Scoreboard();
        Match m = startedMatch(board, "Poland", "Japan");
        assertEquals(MatchStatus.IN_PROGRESS, m.status());
        assertEquals(1, board.inProgressSummary().matches().size());

        MatchResult scored = board.updateScore(m.id(), 2, 1);
        assertTrue(scored.errors().isEmpty());
        assertEquals(2, board.inProgressSummary().matches().get(0).homeScore());

        MatchResult finished = board.finishMatch(m.id());
        assertTrue(finished.errors().isEmpty());
        assertEquals(MatchStatus.FINISHED, finished.match().status());
        assertTrue(board.inProgressSummary().matches().isEmpty());
    }

    @Test
    void scoreUpdateIsAbsoluteAndIdempotent() {
        Scoreboard board = new Scoreboard();
        Match m = startedMatch(board, "Peru", "Chile");

        board.updateScore(m.id(), 3, 1);
        MatchResult again = board.updateScore(m.id(), 3, 1);

        assertTrue(again.errors().isEmpty());
        assertEquals(3, again.match().homeScore());
        assertEquals(1, again.match().awayScore());
    }

    @Test
    void sameTeamsCanPlaySimultaneously() {
        Scoreboard board = new Scoreboard();
        Match first = startedMatch(board, "Germany", "France");
        Match second = startedMatch(board, "Germany", "France");

        assertNotEqualsIds(first.id(), second.id());
        assertEquals(2, board.inProgressSummary().matches().size());
    }

    private void assertNotEqualsIds(MatchId a, MatchId b) {
        assertTrue(a.value() != b.value(), "ids must differ");
    }

    // --- errors as data ---

    @Test
    void blankTeamNamesRejected() {
        Scoreboard board = new Scoreboard();
        MatchResult result = board.scheduleMatch("  ", "Japan");

        assertNull(result.match());
        assertEquals(1, result.errors().size());
    }

    @Test
    void unknownIdRejectedWithoutThrowing() {
        Scoreboard board = new Scoreboard();
        MatchId unknown = new MatchId(999);

        for (MatchResult r : List.of(
                board.startMatch(unknown),
                board.updateScore(unknown, 1, 1),
                board.finishMatch(unknown),
                board.getMatch(unknown))) {
            assertNull(r.match());
            assertEquals(1, r.errors().size());
            assertTrue(r.errors().get(0).contains("Match not found"));
        }
    }

    @Test
    void illegalTransitionsIgnoredAndReported() {
        Scoreboard board = new Scoreboard();
        Match planned = board.scheduleMatch("Egypt", "Saudi Arabia").match();

        // cannot score before start
        MatchResult early = board.updateScore(planned.id(), 1, 0);
        assertEquals(1, early.errors().size());
        assertEquals(0, early.match().homeScore());

        // cannot finish before start
        assertEquals(1, board.finishMatch(planned.id()).errors().size());

        Match started = board.startMatch(planned.id()).match();
        // cannot start twice
        MatchResult restart = board.startMatch(planned.id());
        assertEquals(1, restart.errors().size());
        assertEquals(MatchStatus.IN_PROGRESS, restart.match().status());

        board.finishMatch(planned.id());
        // cannot update or finish a finished match
        MatchResult lateGoal = board.updateScore(planned.id(), 5, 5);
        assertEquals(1, lateGoal.errors().size());
        assertEquals(0, lateGoal.match().homeScore());
        assertEquals(1, board.finishMatch(planned.id()).errors().size());
    }

    @Test
    void negativeScoresRejected() {
        Scoreboard board = new Scoreboard();
        Match m = startedMatch(board, "Morocco", "Croatia");

        MatchResult result = board.updateScore(m.id(), -1, 0);
        assertEquals(1, result.errors().size());
        assertNull(result.match());
        assertEquals(0, board.getMatch(m.id()).match().homeScore());
    }

    @Test
    void errorsAreNeverNull() {
        Scoreboard board = new Scoreboard();
        assertNotNull(board.scheduleMatch("A", "B").errors());
        assertNotNull(board.inProgressSummary().errors());
        assertNotNull(board.getMatch(new MatchId(-1)).errors());
    }

    // --- the additional operation ---

    @Test
    void getMatchReturnsFinishedMatchDetails() {
        Scoreboard board = new Scoreboard();
        Match m = startedMatch(board, "Senegal", "Ecuador");
        board.updateScore(m.id(), 2, 0);
        board.finishMatch(m.id());

        MatchResult result = board.getMatch(m.id());
        assertTrue(result.errors().isEmpty());
        assertEquals(MatchStatus.FINISHED, result.match().status());
        assertEquals(2, result.match().homeScore());
    }
}
