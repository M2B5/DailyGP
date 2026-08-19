package com.github.M2B5.dailyGP;

import com.github.M2B5.dailyGP.command.DailyGPCommand;
import com.github.M2B5.dailyGP.config.DailyGPConfig;
import com.github.M2B5.dailyGP.grandprix.ExcludedTrackQueue;
import com.github.M2B5.dailyGP.grandprix.GrandPrixManager;
import com.github.M2B5.dailyGP.grandprix.TrackSelector;
import com.github.M2B5.dailyGP.hologram.FancyHologramsInterface;
import com.github.M2B5.dailyGP.hologram.LeaderboardManager;
import com.github.M2B5.dailyGP.listener.HeatProgressListener;
import com.github.M2B5.dailyGP.listener.PlayerJoinListener;
import com.github.M2B5.dailyGP.listener.ScoreboardListener;
import com.github.M2B5.dailyGP.results.ResultsStore;
import com.github.M2B5.dailyGP.schedule.DailyGPScheduler;
import com.github.M2B5.dailyGP.stats.StatisticsManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class DailyGP extends JavaPlugin {

    private static final String COMMAND_NAME = "dailygp";

    private StatisticsManager statisticsManager;
    private ResultsStore resultsStore;
    private ExcludedTrackQueue excludedTracks;
    private LeaderboardManager leaderboardManager;
    private GrandPrixManager grandPrixManager;
    private DailyGPScheduler scheduler;

    @Override
    public void onEnable() {
        if (!isTimingSystemReady()) {
            getLogger().severe("TimingSystem not found.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        DailyGPConfig config = DailyGPConfig.load(getConfig(), getLogger());

        statisticsManager = new StatisticsManager(getDataFolder(), getLogger());
        resultsStore = new ResultsStore(getDataFolder(), getLogger());
        excludedTracks = new ExcludedTrackQueue(getDataFolder(), config.excludedTrackQueueLength(), getLogger());

        leaderboardManager = new LeaderboardManager(new FancyHologramsInterface(this), statisticsManager, resultsStore,
                config, getLogger());
        grandPrixManager = new GrandPrixManager(this, config, new TrackSelector(getLogger(), excludedTracks),
                statisticsManager, resultsStore, leaderboardManager);
        scheduler = new DailyGPScheduler(this, grandPrixManager);

        getServer().getPluginManager().registerEvents(new HeatProgressListener(grandPrixManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, grandPrixManager), this);
        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);
        registerCommand();

        leaderboardManager.createLeaderboards();
        scheduler.start();
        getLogger().info("DailyGP enabled.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
            scheduler = null;
        }
        if (grandPrixManager != null) {
            grandPrixManager.shutdown();
            grandPrixManager = null;
        }
        if (leaderboardManager != null) {
            leaderboardManager.deleteLeaderboards();
            leaderboardManager = null;
        }
        if (statisticsManager != null) {
            statisticsManager.save();
            statisticsManager = null;
        }
        getLogger().info("DailyGP disabled.");
    }

    public void reload() {
        reloadConfig();
        DailyGPConfig config = DailyGPConfig.load(getConfig(), getLogger());

        grandPrixManager.setConfig(config);
        excludedTracks.setCapacity(config.excludedTrackQueueLength());

        leaderboardManager.deleteLeaderboards();
        leaderboardManager.setConfig(config);
        leaderboardManager.createLeaderboards();

        scheduler.restart();
    }

    public GrandPrixManager getGrandPrixManager() {
        return grandPrixManager;
    }

    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public ResultsStore getResultsStore() {
        return resultsStore;
    }

    public ExcludedTrackQueue getExcludedTracks() {
        return excludedTracks;
    }

    private boolean isTimingSystemReady() {
        Plugin timingSystem = getServer().getPluginManager().getPlugin("TimingSystem");
        return timingSystem != null && timingSystem.isEnabled();
    }

    private void registerCommand() {
        PluginCommand command = getCommand(COMMAND_NAME);
        if (command == null) {
            getLogger().severe("Command does not exist.");
            return;
        }
        DailyGPCommand executor = new DailyGPCommand(this, grandPrixManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
