package com.example.scoreboard;

/**
 * Unique, monotonically increasing identifier of a match.
 * The value also doubles as the ordering sequence used for tie-breaks.
 */
public record MatchId(long value) {

    @Override
    public String toString() {
        return "M-" + value;
    }
}
