package com.example.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scoreboard must tolerate concurrent use: no lost updates, no corrupted reads.
 */
class ConcurrencyTest {

    @Test
    void parallelSchedulingProducesUniqueMatches() throws Exception {
        Scoreboard board = new Scoreboard();
        int threads = 8;
        int perThread = 20;

        runInParallel(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                MatchId id = board.scheduleMatch("Home" + i, "Away" + i).match().id();
                board.startMatch(id);
            }
            return null;
        });

        Set<Long> ids = board.inProgressSummary().matches().stream()
                .map(m -> m.id().value()).collect(Collectors.toSet());
        assertEquals(threads * perThread, ids.size());
    }

    @Test
    void concurrentScoreUpdatesNeverCorruptState() throws Exception {
        Scoreboard board = new Scoreboard();
        MatchId id = board.scheduleMatch("Fast", "Furious").match().id();
        board.startMatch(id);

        List<Integer> scores = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        runInParallel(scores.size(), () -> {
            for (int s : scores) {
                board.updateScore(id, s, s);
                SummaryResult snapshot = board.inProgressSummary();
                assertTrue(snapshot.errors().isEmpty());
                snapshot.matches().forEach(m -> assertTrue(m.homeScore() == m.awayScore()));
            }
            return null;
        });

        Match finalMatch = board.getMatch(id).match();
        assertTrue(scores.contains(finalMatch.homeScore()),
                "final score must be one of the concurrently written values");
    }

    private void runInParallel(int tasks, Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
