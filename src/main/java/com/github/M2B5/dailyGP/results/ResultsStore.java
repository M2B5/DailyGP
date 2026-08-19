package com.github.M2B5.dailyGP.results;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class ResultsStore {

    private static final String FILE_NAME = "last_grand_prix.yml";

    private final Path resultsFilePath;
    private final Logger logger;

    private volatile GrandPrixResult latestResult;

    public ResultsStore(File dataFolder, Logger logger) {
        this.resultsFilePath = dataFolder.toPath().resolve(FILE_NAME);
        this.logger = logger;
        load();
    }

    public Optional<GrandPrixResult> getLatestResult() {
        return Optional.ofNullable(latestResult);
    }

    public void setLatestResult(GrandPrixResult result) {
        this.latestResult = result;
        save();
    }

    private void save() {
        if (latestResult == null) {
            return;
        }

        try {
            Files.createDirectories(resultsFilePath.getParent());

            YamlConfiguration config = new YamlConfiguration();
            config.set("track", latestResult.trackName());
            config.set("totalLaps", latestResult.totalLaps());
            config.set("finishedAt", latestResult.finishedAt());

            for (GrandPrixResult.Entry entry : latestResult.entries()) {
                String key = "entries." + entry.position();
                config.set(key + ".uuid", entry.playerId().toString());
                config.set(key + ".name", entry.playerName());
                config.set(key + ".laps", entry.laps());
                config.set(key + ".raceTime", entry.raceTime());
                config.set(key + ".completed", entry.completed());
            }

            config.save(resultsFilePath.toFile());

        } catch (IOException e) {
            logger.severe("Failed to save " + FILE_NAME + ": " + e.getMessage()
                    + ". The results leaderboard will be empty after a restart.");
        }
    }

    private void load() {
        if (!Files.exists(resultsFilePath)) {
            logger.info("No " + FILE_NAME + " yet, so no Grand Prix results to show.");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(resultsFilePath.toFile());

            String trackName = config.getString("track");
            ConfigurationSection entriesSection = config.getConfigurationSection("entries");
            if (trackName == null || entriesSection == null) {
                logger.warning(FILE_NAME + " is missing its track or entries, ignoring it.");
                return;
            }

            List<GrandPrixResult.Entry> entries = new ArrayList<>();
            for (String key : entriesSection.getKeys(false)) {
                ConfigurationSection entry = entriesSection.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                try {
                    entries.add(new GrandPrixResult.Entry(
                            UUID.fromString(entry.getString("uuid", "")),
                            entry.getString("name", "Unknown"),
                            Integer.parseInt(key),
                            entry.getInt("laps"),
                            entry.getLong("raceTime"),
                            entry.getBoolean("completed")));
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping entry '" + key + "' in " + FILE_NAME + ": " + e.getMessage());
                }
            }

            entries.sort((first, second) -> Integer.compare(first.position(), second.position()));
            latestResult = new GrandPrixResult(trackName, config.getInt("totalLaps"), config.getLong("finishedAt"),
                    entries);
            logger.info("Loaded the results of the last Grand Prix at " + trackName + ".");

        } catch (Exception e) {
            logger.severe("Failed to read " + FILE_NAME + ": " + e.getMessage()
                    + ". The results leaderboard will stay empty until the next Grand Prix.");
        }
    }
}
