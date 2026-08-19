package com.github.M2B5.dailyGP.grandprix;

import me.makkuusen.timing.system.track.Track;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;

public class ExcludedTrackQueue {

    private static final String FILE_NAME = "excluded_tracks.yml";

    private final Path queueFilePath;
    private final Logger logger;

    private final Deque<Integer> trackIds = new ArrayDeque<>();
    private int capacity;

    public ExcludedTrackQueue(File dataFolder, int capacity, Logger logger) {
        this.queueFilePath = dataFolder.toPath().resolve(FILE_NAME);
        this.logger = logger;
        this.capacity = Math.max(0, capacity);
        load();
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(0, capacity);
        if (trim()) {
            save();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isExcluded(Track track) {
        return trackIds.contains(track.getId());
    }

    public void add(Track track) {
        if (capacity == 0) {
            return;
        }

        trackIds.remove(track.getId());
        trackIds.addFirst(track.getId());
        trim();
        save();
    }

    public List<Integer> getTrackIds() {
        return List.copyOf(trackIds);
    }

    public void clear() {
        trackIds.clear();
        save();
    }

    private boolean trim() {
        boolean dropped = false;
        while (trackIds.size() > capacity) {
            trackIds.removeLast();
            dropped = true;
        }
        return dropped;
    }

    private void save() {
        try {
            Files.createDirectories(queueFilePath.getParent());

            YamlConfiguration config = new YamlConfiguration();
            config.set("trackIds", new ArrayList<>(trackIds));
            config.save(queueFilePath.toFile());

        } catch (IOException e) {
            logger.severe("Failed to save " + FILE_NAME + ": " + e.getMessage()
                    + ". Recently raced tracks may be drawn again after a restart.");
        }
    }

    private void load() {
        if (!Files.exists(queueFilePath)) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(queueFilePath.toFile());
            trackIds.addAll(config.getIntegerList("trackIds"));
            trim();
            logger.info("Holding back " + trackIds.size() + " recently raced track(s) from the Grand Prix draw.");

        } catch (Exception e) {
            logger.severe("Failed to read " + FILE_NAME + ": " + e.getMessage()
                    + ". Every track is available to be drawn.");
            trackIds.clear();
        }
    }
}
