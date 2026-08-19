package com.github.M2B5.dailyGP.hologram;

import com.github.M2B5.dailyGP.config.DailyGPConfig;
import com.github.M2B5.dailyGP.results.GrandPrixResult;
import com.github.M2B5.dailyGP.results.ResultsStore;
import com.github.M2B5.dailyGP.stats.PlayerStats;
import com.github.M2B5.dailyGP.stats.StatisticsManager;
import com.github.M2B5.dailyGP.util.FontInfo;
import com.github.M2B5.dailyGP.util.Palette;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

public class LeaderboardManager {

    private static final String LATEST_RESULTS_HOLOGRAM_ID = "dailygp_latest_results";
    private static final String TOTAL_WINS_HOLOGRAM_ID = "dailygp_total_wins";
    private static final String WIN_STREAKS_HOLOGRAM_ID = "dailygp_win_streaks";

    private static final String LATEST_RESULTS_HEADER = Palette.header("LATEST GRAND PRIX");
    private static final String TOTAL_WINS_HEADER = Palette.header("ALL-TIME WINS");
    private static final String WIN_STREAKS_HEADER = Palette.header("WIN STREAKS");

    private static final int TOP_PLAYERS_COUNT = 10;

    private static final int MAX_NAME_WIDTH_NORMAL = 96;
    private static final int MAX_NAME_WIDTH_BOLD = 112;

    private static final int LEADERBOARD_WIDTH = 16 + MAX_NAME_WIDTH_NORMAL + 10 + 48;

    private final FancyHologramsInterface holograms;
    private final StatisticsManager statistics;
    private final ResultsStore results;
    private final Logger logger;

    private DailyGPConfig config;

    public LeaderboardManager(FancyHologramsInterface holograms, StatisticsManager statistics, ResultsStore results,
                              DailyGPConfig config, Logger logger) {
        this.holograms = holograms;
        this.statistics = statistics;
        this.results = results;
        this.config = config;
        this.logger = logger;
    }

    public void setConfig(DailyGPConfig config) {
        this.config = config;
    }

    public void createLeaderboards() {
        if (!holograms.isAvailable()) {
            logger.info("FancyHolograms is not installed, so the Grand Prix leaderboards are disabled.");
            return;
        }

        create(LATEST_RESULTS_HOLOGRAM_ID, config.latestResultsHologram(), LATEST_RESULTS_HEADER, "latest results");
        create(TOTAL_WINS_HOLOGRAM_ID, config.totalWinsHologram(), TOTAL_WINS_HEADER, "total wins");
        create(WIN_STREAKS_HOLOGRAM_ID, config.winStreaksHologram(), WIN_STREAKS_HEADER, "win streaks");

        refresh();
    }

    private void create(String id, Location location, String header, String description) {
        if (location == null) {
            logger.info("No location configured for the " + description + " leaderboard, skipping it.");
            return;
        }
        if (holograms.createHologram(id, location, List.of(centerLine(header, true), buildDivider(),
                centerLine("§8Loading...", false)))) {
            logger.info("Placed the " + description + " leaderboard.");
        }
    }

    public void refresh() {
        if (!holograms.isAvailable()) {
            return;
        }

        updateLatestResults();
        updateTotalWins();
        updateWinStreaks();
    }

    public void deleteLeaderboards() {
        if (!holograms.isAvailable()) {
            return;
        }

        holograms.deleteHologram(LATEST_RESULTS_HOLOGRAM_ID);
        holograms.deleteHologram(TOTAL_WINS_HOLOGRAM_ID);
        holograms.deleteHologram(WIN_STREAKS_HOLOGRAM_ID);
    }

    private void updateLatestResults() {
        List<String> lines = new ArrayList<>();
        lines.add(centerLine(LATEST_RESULTS_HEADER, true));

        Optional<GrandPrixResult> maybeResult = results.getLatestResult();
        if (maybeResult.isEmpty()) {
            lines.add(buildDivider());
            lines.add(centerLine("§8No Grand Prix has been run yet", false));
            holograms.updateHologram(LATEST_RESULTS_HOLOGRAM_ID, lines);
            return;
        }

        GrandPrixResult result = maybeResult.get();
        lines.add(centerLine("§8Track: §7" + result.trackName() + " §8(§7" + result.totalLaps() + " laps§8)", false));
        lines.add(buildDivider());

        if (result.entries().isEmpty()) {
            lines.add(centerLine("§8Nobody was classified", false));
        } else {
            for (GrandPrixResult.Entry entry : result.entries().stream().limit(TOP_PLAYERS_COUNT).toList()) {
                String value = entry.completed()
                        ? "§a" + formatTime(entry.raceTime())
                        : "§cDNF §8(" + entry.laps() + " laps)";
                lines.add(getRankColor(entry.position()) + "#" + entry.position() + " §f"
                        + padName(entry.playerName(), false) + " §8| " + value);
            }
        }

        holograms.updateHologram(LATEST_RESULTS_HOLOGRAM_ID, lines);
    }

    private void updateTotalWins() {
        holograms.updateHologram(TOTAL_WINS_HOLOGRAM_ID,
                buildStatsBoard(TOTAL_WINS_HEADER, PlayerStats::getTotalWins, "wins", "§8No wins recorded yet"));
    }

    private void updateWinStreaks() {
        holograms.updateHologram(WIN_STREAKS_HOLOGRAM_ID,
                buildStatsBoard(WIN_STREAKS_HEADER, PlayerStats::getHighestStreak, "streak",
                        "§8No streaks recorded yet"));
    }

    private List<String> buildStatsBoard(String header, ToIntFunction<PlayerStats> statistic, String unit,
                                         String emptyText) {
        List<PlayerStats> ranked = statistics.getStats().stream()
                .filter(stats -> statistic.applyAsInt(stats) > 0)
                .sorted(Comparator.comparingInt(statistic).reversed())
                .limit(TOP_PLAYERS_COUNT)
                .toList();

        List<String> lines = new ArrayList<>();
        lines.add(centerLine(header, true));
        lines.add(buildDivider());

        if (ranked.isEmpty()) {
            lines.add(centerLine(emptyText, false));
            return lines;
        }

        for (int i = 0; i < ranked.size(); i++) {
            PlayerStats stats = ranked.get(i);
            int rank = i + 1;
            lines.add(getRankColor(rank) + "#" + rank + " §f" + padName(getPlayerName(stats.getPlayerId()), false)
                    + " §8| §a" + statistic.applyAsInt(stats) + " " + unit);
        }
        return lines;
    }

    private String padName(String name, boolean bold) {
        int target = bold ? MAX_NAME_WIDTH_BOLD : MAX_NAME_WIDTH_NORMAL;
        return name + FontInfo.spacesTo(FontInfo.getStringWidth(name, bold), target);
    }

    private String buildDivider() {
        return Palette.divider(LEADERBOARD_WIDTH);
    }

    private String centerLine(String text, boolean bold) {
        return Palette.center(text, bold, LEADERBOARD_WIDTH);
    }

    private String getRankColor(int rank) {
        return switch (rank) {
            case 1 -> "§x§F§F§D§7§0§0";
            case 2 -> "§x§C§0§C§0§C§0";
            case 3 -> "§x§C§D§7§F§3§2";
            default -> "§7";
        };
    }

    private String formatTime(long timeMillis) {
        long totalSeconds = timeMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = timeMillis % 1000;

        return minutes > 0
                ? String.format("%d:%02d.%03d", minutes, seconds, millis)
                : String.format("%d.%03d", seconds, millis);
    }

    private String getPlayerName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? "Unknown" : name;
    }
}
