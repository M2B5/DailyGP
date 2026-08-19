package com.github.M2B5.dailyGP.schedule;

import com.github.M2B5.dailyGP.config.DailyGPConfig;
import com.github.M2B5.dailyGP.grandprix.GrandPrixManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DailyGPScheduler {

    private static final long CHECK_INTERVAL_TICKS = 20L;

    private final Plugin plugin;
    private final Logger logger;
    private final GrandPrixManager manager;

    private BukkitTask task;
    private LocalDateTime lastCheck;

    public DailyGPScheduler(Plugin plugin, GrandPrixManager manager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.manager = manager;
    }

    public void start() {
        DailyGPConfig config = manager.getConfig();
        lastCheck = LocalDateTime.now(config.timezone());

        catchUp(config);

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
        logger.info("Track selection at " + DailyGPConfig.format(config.trackSelectionTime()) + ", qualifying opens at "
                + DailyGPConfig.format(config.startTime()) + " (" + config.timezone() + ").");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    private void tick() {
        DailyGPConfig config = manager.getConfig();
        LocalDateTime now = LocalDateTime.now(config.timezone());

        if (now.isBefore(lastCheck)) {
            lastCheck = now;
            return;
        }

        try {
            if (hasJustPassed(config.trackSelectionTime(), now)) {
                manager.selectTrack();
            }
            if (hasJustPassed(config.startTime(), now)) {
                manager.start();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to run the scheduled Grand Prix step", e);
        } finally {
            lastCheck = now;
        }
    }

    private boolean hasJustPassed(LocalTime timeOfDay, LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atTime(timeOfDay);
        LocalDateTime yesterday = today.minusDays(1);
        return isBetween(today, now) || isBetween(yesterday, now);
    }

    private boolean isBetween(LocalDateTime candidate, LocalDateTime now) {
        return candidate.isAfter(lastCheck) && !candidate.isAfter(now);
    }

    private void catchUp(DailyGPConfig config) {
        LocalDateTime now = LocalDateTime.now(config.timezone());
        LocalDateTime selection = now.toLocalDate().atTime(config.trackSelectionTime());
        LocalDateTime start = now.toLocalDate().atTime(config.startTime());

        if (!now.isBefore(selection) && now.isBefore(start)) {
            logger.info("Starting up after today's track selection time, drawing the Grand Prix track now.");
            manager.selectTrack();
        }
    }
}
