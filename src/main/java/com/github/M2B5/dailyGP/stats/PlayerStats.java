package com.github.M2B5.dailyGP.stats;

import java.util.UUID;

public class PlayerStats {

    private final UUID playerId;
    private int totalWins;
    private int currentStreak;
    private int highestStreak;

    public PlayerStats(UUID playerId) {
        this(playerId, 0, 0, 0);
    }

    public PlayerStats(UUID playerId, int totalWins, int currentStreak, int highestStreak) {
        this.playerId = playerId;
        this.totalWins = totalWins;
        this.currentStreak = currentStreak;
        this.highestStreak = highestStreak;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getHighestStreak() {
        return highestStreak;
    }

    public void setHighestStreak(int highestStreak) {
        this.highestStreak = highestStreak;
    }
}
