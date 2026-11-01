package com.example.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance test taken verbatim from the "Example Scenario" section of the task.
 */
class ExampleScenarioTest {

    @Test
    void exampleScenarioProducesExpectedOrdering() {
        Scoreboard board = new Scoreboard();

        // started in this order, then updated with these scores
        record Fixture(String home, String away, int homeScore, int awayScore) {
        }
        List<Fixture> fixtures = List.of(
                new Fixture("Mexico", "Canada", 0, 5),
                new Fixture("Spain", "Brazil", 10, 2),
                new Fixture("Germany", "France", 2, 2),
                new Fixture("Uruguay", "Italy", 6, 6),
                new Fixture("Argentina", "Australia", 3, 1));

        for (Fixture f : fixtures) {
            MatchResult scheduled = board.scheduleMatch(f.home(), f.away());
            assertTrue(scheduled.errors().isEmpty(), scheduled.errors().toString());
            MatchResult started = board.startMatch(scheduled.match().id());
            assertTrue(started.errors().isEmpty(), started.errors().toString());
            MatchResult scored = board.updateScore(scheduled.match().id(), f.homeScore(), f.awayScore());
            assertTrue(scored.errors().isEmpty(), scored.errors().toString());
        }

        SummaryResult summary = board.inProgressSummary();
        assertTrue(summary.errors().isEmpty());

        List<String> actual = summary.matches().stream()
                .map(m -> m.home() + " " + m.homeScore() + " - " + m.awayScore() + " " + m.away())
                .toList();

        assertEquals(List.of(
                "Uruguay 6 - 6 Italy",     // total 12, started after Spain
                "Spain 10 - 2 Brazil",     // total 12
                "Mexico 0 - 5 Canada",     // total 5
                "Argentina 3 - 1 Australia", // total 4, started after Germany
                "Germany 2 - 2 France"     // total 4
        ), actual);
    }
}
