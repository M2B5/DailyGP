package com.github.M2B5.dailyGP.stats;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class StatisticsManager {

    private static final String FILE_NAME = "player_stats.yml";

    private final Path statsFilePath;
    private final Logger logger;
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();

    public StatisticsManager(File dataFolder, Logger logger) {
        this.statsFilePath = dataFolder.toPath().resolve(FILE_NAME);
        this.logger = logger;
        load();
    }

    public void recordWin(UUID playerId) {
        PlayerStats stats = playerStats.computeIfAbsent(playerId, PlayerStats::new);

        stats.setTotalWins(stats.getTotalWins() + 1);
        stats.setCurrentStreak(stats.getCurrentStreak() + 1);
        if (stats.getCurrentStreak() > stats.getHighestStreak()) {
            stats.setHighestStreak(stats.getCurrentStreak());
        }

        logger.info("Recorded a Grand Prix win for " + playerId + ": " + stats.getTotalWins() + " wins, "
                + stats.getCurrentStreak() + " in a row (best " + stats.getHighestStreak() + ").");
        save();
    }

    public void endStreaksExcept(UUID winner) {
        boolean changed = false;

        for (PlayerStats stats : playerStats.values()) {
            if (stats.getPlayerId().equals(winner) || stats.getCurrentStreak() == 0) {
                continue;
            }
            stats.setCurrentStreak(0);
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    public int getTotalWins(UUID playerId) {
        PlayerStats stats = playerStats.get(playerId);
        return stats == null ? 0 : stats.getTotalWins();
    }

    public int getCurrentStreak(UUID playerId) {
        PlayerStats stats = playerStats.get(playerId);
        return stats == null ? 0 : stats.getCurrentStreak();
    }

    public int getHighestStreak(UUID playerId) {
        PlayerStats stats = playerStats.get(playerId);
        return stats == null ? 0 : stats.getHighestStreak();
    }

    public Map<UUID, PlayerStats> getAllStats() {
        return Collections.unmodifiableMap(playerStats);
    }

    public Collection<PlayerStats> getStats() {
        return Collections.unmodifiableCollection(playerStats.values());
    }

    public void save() {
        try {
            Files.createDirectories(statsFilePath.getParent());

            YamlConfiguration config = new YamlConfiguration();
            for (PlayerStats stats : playerStats.values()) {
                String key = stats.getPlayerId().toString();
                config.set(key + ".totalWins", stats.getTotalWins());
                config.set(key + ".currentStreak", stats.getCurrentStreak());
                config.set(key + ".highestStreak", stats.getHighestStreak());
            }
            config.save(statsFilePath.toFile());

        } catch (IOException e) {
            logger.severe("Failed to save " + FILE_NAME + ": " + e.getMessage()
                    + ". Grand Prix wins recorded since the last successful save may be lost.");
        }
    }

    public void load() {
        playerStats.clear();

        if (!Files.exists(statsFilePath)) {
            logger.info("No " + FILE_NAME + " yet, starting with an empty record.");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(statsFilePath.toFile());

            for (String key : config.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    playerStats.put(playerId, new PlayerStats(
                            playerId,
                            config.getInt(key + ".totalWins", 0),
                            config.getInt(key + ".currentStreak", 0),
                            config.getInt(key + ".highestStreak", 0)));
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping '" + key + "' in " + FILE_NAME + ": not a player UUID.");
                }
            }

            logger.info("Loaded Grand Prix statistics for " + playerStats.size() + " drivers.");

        } catch (Exception e) {
            logger.severe("Failed to read " + FILE_NAME + ": " + e.getMessage() + ". Starting with an empty record.");
            backupCorruptedFile();
        }
    }

    private void backupCorruptedFile() {
        try {
            Path backup = statsFilePath.resolveSibling(FILE_NAME + ".backup");
            Files.copy(statsFilePath, backup, StandardCopyOption.REPLACE_EXISTING);
            logger.info("The unreadable statistics file was backed up to " + backup + ".");
        } catch (IOException e) {
            logger.severe("Failed to back up the unreadable statistics file: " + e.getMessage());
        }
    }
}
