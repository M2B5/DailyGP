package com.github.M2B5.dailyGP.results;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record GrandPrixResult(String trackName, int totalLaps, long finishedAt, List<Entry> entries) {

    public GrandPrixResult {
        entries = List.copyOf(entries);
    }

    public record Entry(UUID playerId, String playerName, int position, int laps, long raceTime, boolean completed) {
    }

    public Optional<Entry> getWinner() {
        return entries.stream()
                .filter(entry -> entry.position() == 1)
                .filter(Entry::completed)
                .findFirst();
    }
}
