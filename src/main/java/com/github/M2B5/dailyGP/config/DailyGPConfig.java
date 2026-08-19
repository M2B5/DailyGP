package com.github.M2B5.dailyGP.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public record DailyGPConfig(
        ZoneId timezone,
        LocalTime trackSelectionTime,
        LocalTime startTime,
        String trackTag,
        int maxDrivers,
        int pits,
        int countdownSeconds,
        int excludedTrackQueueLength,
        Duration qualifyingDuration,
        Duration qualifyingGrace,
        Duration raceDuration,
        Duration raceGrace,
        double paceMultiplier,
        boolean drs,
        boolean pushToPass,
        Location latestResultsHologram,
        Location totalWinsHologram,
        Location winStreaksHologram
) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final LocalTime DEFAULT_SELECTION_TIME = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(20, 0);
    private static final String DEFAULT_TRACK_TAG = "DailyGP";

    public static DailyGPConfig load(FileConfiguration config, Logger logger) {
        ZoneId timezone = parseZone(config.getString("schedule.timezone", ""), logger);
        LocalTime selectionTime = parseTime(config.getString("schedule.track-selection-time"),
                DEFAULT_SELECTION_TIME, "schedule.track-selection-time", logger);
        LocalTime startTime = parseTime(config.getString("schedule.start-time"),
                DEFAULT_START_TIME, "schedule.start-time", logger);

        if (!selectionTime.isBefore(startTime)) {
            logger.warning("schedule.track-selection-time (" + selectionTime.format(TIME_FORMAT) + ") is not before "
                    + "schedule.start-time (" + startTime.format(TIME_FORMAT) + "), so each day's track will only be "
                    + "announced in time for the following day's Grand Prix.");
        }

        String trackTag = config.getString("track-tag", DEFAULT_TRACK_TAG);
        if (trackTag == null || trackTag.isBlank()) {
            logger.warning("track-tag is empty, falling back to '" + DEFAULT_TRACK_TAG + "'.");
            trackTag = DEFAULT_TRACK_TAG;
        }

        return new DailyGPConfig(
                timezone,
                selectionTime,
                startTime,
                trackTag.trim(),
                clamp(config.getInt("grand-prix.max-drivers", 12), 1, 100, "grand-prix.max-drivers", logger),
                clamp(config.getInt("grand-prix.pits", 1), 0, 10, "grand-prix.pits", logger),
                clamp(config.getInt("grand-prix.countdown-seconds", 10), 1, 60, "grand-prix.countdown-seconds", logger),
                clamp(config.getInt("grand-prix.excluded-track-queue-length", 5), 0, 100,
                        "grand-prix.excluded-track-queue-length", logger),
                Duration.ofMinutes(clamp(config.getInt("qualifying.duration-minutes", 10), 1, 240,
                        "qualifying.duration-minutes", logger)),
                Duration.ofSeconds(clamp(config.getInt("qualifying.finish-grace-seconds", 120), 0, 3600,
                        "qualifying.finish-grace-seconds", logger)),
                Duration.ofMinutes(clamp(config.getInt("race.duration-minutes", 30), 1, 240,
                        "race.duration-minutes", logger)),
                Duration.ofSeconds(clamp(config.getInt("race.finish-grace-seconds", 180), 0, 3600,
                        "race.finish-grace-seconds", logger)),
                clamp(config.getDouble("race.pace-multiplier", 1.1), 1.0, 3.0, "race.pace-multiplier", logger),
                config.getBoolean("race.drs", true),
                config.getBoolean("race.push-to-pass", false),
                parseLocation(config, "holograms.latest-results", logger),
                parseLocation(config, "holograms.total-wins", logger),
                parseLocation(config, "holograms.win-streaks", logger)
        );
    }

    private static Location parseLocation(FileConfiguration config, String path, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            logger.warning(path + " has no world set, so that leaderboard will not be shown.");
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warning("World '" + worldName + "' for " + path + " is not loaded, so that leaderboard will not "
                    + "be shown.");
            return null;
        }

        if (!section.contains("x") || !section.contains("y") || !section.contains("z")) {
            logger.warning(path + " is missing x, y or z, so that leaderboard will not be shown.");
            return null;
        }

        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0), (float) section.getDouble("pitch", 0.0));
    }

    private static ZoneId parseZone(String raw, Logger logger) {
        if (raw == null || raw.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            logger.warning("Unknown timezone '" + raw + "' in schedule.timezone, using the server timezone ("
                    + ZoneId.systemDefault() + ") instead.");
            return ZoneId.systemDefault();
        }
    }

    private static LocalTime parseTime(String raw, LocalTime fallback, String path, Logger logger) {
        if (raw == null || raw.isBlank()) {
            logger.warning(path + " is not set, using " + fallback.format(TIME_FORMAT) + ".");
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FORMAT);
        } catch (DateTimeException e) {
            logger.warning("Could not read " + path + " ('" + raw + "'), expected HH:mm. Using "
                    + fallback.format(TIME_FORMAT) + " instead.");
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max, String path, Logger logger) {
        int clamped = Math.min(max, Math.max(min, value));
        if (clamped != value) {
            logger.warning(path + " must be between " + min + " and " + max + ", using " + clamped
                    + " instead of " + value + ".");
        }
        return clamped;
    }

    private static double clamp(double value, double min, double max, String path, Logger logger) {
        double clamped = Math.min(max, Math.max(min, value));
        if (clamped != value) {
            logger.warning(path + " must be between " + min + " and " + max + ", using " + clamped
                    + " instead of " + value + ".");
        }
        return clamped;
    }

    public static String format(LocalTime time) {
        return time.format(TIME_FORMAT);
    }
}
