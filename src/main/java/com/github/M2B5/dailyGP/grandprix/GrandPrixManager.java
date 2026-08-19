package com.github.M2B5.dailyGP.grandprix;

import com.github.M2B5.dailyGP.config.DailyGPConfig;
import com.github.M2B5.dailyGP.hologram.LeaderboardManager;
import com.github.M2B5.dailyGP.message.GrandPrixCard;
import com.github.M2B5.dailyGP.results.GrandPrixResult;
import com.github.M2B5.dailyGP.results.ResultsStore;
import com.github.M2B5.dailyGP.stats.StatisticsManager;
import com.github.M2B5.dailyGP.util.Announcer;
import com.github.M2B5.dailyGP.util.Palette;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.event.EventCountdown;
import me.makkuusen.timing.system.heat.CollisionMode;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GrandPrixManager {

    private static final UUID EVENT_OWNER = new UUID(0, 0);

    private static final DateTimeFormatter EVENT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private static final long GRID_SETTLE_TICKS = 60L;

    private static final long INTERMISSION_TICKS = 100L;

    private static final long RESULTS_TICKS = 40L;

    private static final Duration WATCHDOG_HEADROOM = Duration.ofMinutes(2);

    private static final int RACE_WATCHDOG_FACTOR = 3;

    private final Plugin plugin;
    private final Logger logger;
    private final TrackSelector trackSelector;
    private final StatisticsManager statistics;
    private final ResultsStore results;
    private final LeaderboardManager leaderboards;

    private DailyGPConfig config;

    private GrandPrixState state = GrandPrixState.IDLE;
    private Track track;
    private Event event;
    private Heat qualifyingHeat;
    private Heat finalHeat;

    private BukkitTask graceTask;
    private BukkitTask watchdogTask;
    private BukkitTask emptySessionTask;

    public GrandPrixManager(Plugin plugin, DailyGPConfig config, TrackSelector trackSelector,
                            StatisticsManager statistics, ResultsStore results, LeaderboardManager leaderboards) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
        this.trackSelector = trackSelector;
        this.statistics = statistics;
        this.results = results;
        this.leaderboards = leaderboards;
    }

    public void setConfig(DailyGPConfig config) {
        this.config = config;
    }

    public DailyGPConfig getConfig() {
        return config;
    }

    public GrandPrixState getState() {
        return state;
    }

    public Optional<Track> getTrack() {
        return Optional.ofNullable(track);
    }

    public Optional<Event> getEvent() {
        return Optional.ofNullable(event);
    }

    public Optional<Heat> getQualifyingHeat() {
        return Optional.ofNullable(qualifyingHeat);
    }

    public Optional<Heat> getFinalHeat() {
        return Optional.ofNullable(finalHeat);
    }

    public List<Driver> getDrivers() {
        Heat heat = state == GrandPrixState.FINAL ? finalHeat : qualifyingHeat;
        return heat == null ? List.of() : List.copyOf(heat.getLivePositions());
    }

    public int getMaxDrivers() {
        return track == null ? config.maxDrivers() : getMaxDrivers(track);
    }

    public int getRaceLaps() {
        if (finalHeat != null && finalHeat.getTotalLaps() != null) {
            return finalHeat.getTotalLaps();
        }
        return track == null ? 0
                : LapCalculator.calculateLaps(track, config.raceDuration(), config.paceMultiplier(), logger);
    }

    public int getRacePits() {
        if (finalHeat != null && finalHeat.getTotalPits() != null) {
            return finalHeat.getTotalPits();
        }
        return track == null ? 0 : getPits(track);
    }

    public boolean isRunning() {
        return state == GrandPrixState.QUALIFYING
                || state == GrandPrixState.BETWEEN_HEATS
                || state == GrandPrixState.FINAL;
    }

    public boolean selectTrack() {
        if (state != GrandPrixState.IDLE) {
            logger.info("Skipping track selection: a Grand Prix is already " + describeState() + ".");
            return false;
        }

        Optional<Track> drawnTrack = trackSelector.drawTrack(config.trackTag());
        if (drawnTrack.isEmpty()) {
            return false;
        }

        announceTrack(drawnTrack.get(), Palette.MUTED + "Today's Grand Prix is at",
                Palette.MUTED + "qualifying opens at " + Palette.TEXT + DailyGPConfig.format(config.startTime()));
        return true;
    }

    private void announceTrack(Track drawnTrack, String headline, String opensAt) {
        track = drawnTrack;
        state = GrandPrixState.SCHEDULED;

        int laps = LapCalculator.calculateLaps(track, config.raceDuration(), config.paceMultiplier(), logger);
        logger.info("The Grand Prix track is " + track.getDisplayName() + " (" + laps + " laps).");

        Announcer.broadcastClickable(headline + " " + Palette.TEXT + track.getDisplayName() + Palette.MUTED + ": "
                        + Palette.TEXT + config.qualifyingDuration().toMinutes() + Palette.MUTED + " minutes of "
                        + "qualifying followed by a " + Palette.TEXT + laps + Palette.MUTED + " lap final, " + opensAt
                        + Palette.MUTED + ". Join with " + Palette.ACCENT + "/dailygp join" + Palette.MUTED + ".",
                "/dailygp join", Palette.PRIMARY + "Join the Grand Prix once qualifying is open");
    }

    public boolean start() {
        if (state == GrandPrixState.IDLE && !selectTrack()) {
            return false;
        }
        if (state != GrandPrixState.SCHEDULED) {
            logger.warning("Not starting the Grand Prix: it is already " + describeState() + ".");
            return false;
        }

        if (!createEvent()) {
            abort("the event could not be created");
            return false;
        }

        qualifyingHeat.startHeat();
        state = GrandPrixState.QUALIFYING;
        logger.info("Qualifying at " + track.getDisplayName() + " is open for "
                + describe(config.qualifyingDuration()) + ".");
        Announcer.broadcast(GrandPrixCard.build(this));

        scheduleWatchdog(qualifyingHeat, config.qualifyingDuration()
                .plus(config.qualifyingGrace())
                .plus(WATCHDOG_HEADROOM), "Qualifying");
        scheduleEmptySessionCheck();
        return true;
    }

    public boolean startNow() {
        if (isRunning()) {
            logger.warning("Not calling a Grand Prix: one is already " + describeState() + ".");
            return false;
        }

        Optional<Track> drawnTrack = trackSelector.drawTrack(config.trackTag());
        if (drawnTrack.isEmpty()) {
            return false;
        }

        cancelTasks();
        if (state == GrandPrixState.SCHEDULED) {
            logger.info("Replacing the Grand Prix that was waiting for its scheduled start.");
        }

        announceTrack(drawnTrack.get(), Palette.MUTED + "A Grand Prix has been called at",
                Palette.MUTED + "qualifying opens now");
        return start();
    }

    private boolean createEvent() {
        String name = "DailyGP-" + LocalDateTime.now(config.timezone()).format(EVENT_NAME_FORMAT);
        Optional<Event> createdEvent = EventDatabase.eventNew(EVENT_OWNER, name);
        if (createdEvent.isEmpty()) {
            logger.severe("TimingSystem refused to create the event '" + name + "'.");
            return false;
        }

        event = createdEvent.get();
        event.setTrack(track);
        event.setOpenSign(false);

        if (!EventDatabase.roundNew(event, RoundType.QUALIFICATION, 1)
                || !EventDatabase.roundNew(event, RoundType.FINAL, 2)) {
            logger.severe("Could not create the rounds for event '" + name + "'.");
            return false;
        }

        Optional<Heat> qualifying = createHeat(1);
        Optional<Heat> race = createHeat(2);
        if (qualifying.isEmpty() || race.isEmpty()) {
            logger.severe("Could not create the heats for event '" + name + "'.");
            return false;
        }

        qualifyingHeat = qualifying.get();
        finalHeat = race.get();

        int maxDrivers = getMaxDrivers(track);
        qualifyingHeat.setMaxDrivers(maxDrivers);
        qualifyingHeat.setTimeLimit((int) config.qualifyingDuration().toMillis());
        qualifyingHeat.setLapReset(true);
        qualifyingHeat.setStartDelay(0);
        qualifyingHeat.setCollisionMode(CollisionMode.DISABLED);

        finalHeat.setMaxDrivers(maxDrivers);
        finalHeat.setTotalLaps(LapCalculator.calculateLaps(track, config.raceDuration(), config.paceMultiplier(), logger));
        finalHeat.setTotalPits(getPits(track));
        finalHeat.setDrs(config.drs());
        finalHeat.setPushToPass(config.pushToPass());
        finalHeat.setCollisionMode(CollisionMode.HIGH);
        warnAboutMissingDrsRegions();

        if (!qualifyingHeat.loadHeat()) {
            logger.severe("Could not load the qualifying heat of event '" + name + "'.");
            return false;
        }
        return true;
    }

    private Optional<Heat> createHeat(int roundIndex) {
        Optional<Round> round = event.getEventSchedule().getRound(roundIndex);
        if (round.isEmpty()) {
            return Optional.empty();
        }
        round.get().createHeat(1);
        return round.get().getHeats().stream().findFirst();
    }

    public JoinResult join(Player player) {
        if (state != GrandPrixState.QUALIFYING || qualifyingHeat == null
                || qualifyingHeat.getHeatState() != HeatState.RACING || qualifyingHeat.getStartTime() == null) {
            return JoinResult.NOT_OPEN;
        }
        if (graceTask != null || getQualifyingTimeLeft().isZero()) {
            return JoinResult.CLOSING;
        }

        UUID uuid = player.getUniqueId();
        if (qualifyingHeat.getDrivers().containsKey(uuid)) {
            return JoinResult.ALREADY_RACING;
        }
        if (TimingSystemAPI.getDriverFromRunningHeat(uuid).isPresent()) {
            return JoinResult.IN_ANOTHER_HEAT;
        }
        if (qualifyingHeat.getDrivers().size() >= getMaxDrivers(track)) {
            return JoinResult.FULL;
        }

        if (!EventDatabase.heatDriverNewLate(uuid, qualifyingHeat, qualifyingHeat.getDrivers().size() + 1)) {
            logger.warning("TimingSystem refused to add " + player.getName() + " to " + qualifyingHeat.getName() + ".");
            return JoinResult.FAILED;
        }

        Driver driver = qualifyingHeat.getDrivers().get(uuid);
        if (driver == null) {
            logger.warning("Driver " + player.getName() + " went missing after being added to "
                    + qualifyingHeat.getName() + ".");
            return JoinResult.FAILED;
        }

        qualifyingHeat.addLateDriverToGrid(driver);
        logger.info(player.getName() + " joined qualifying (" + qualifyingHeat.getDrivers().size() + "/"
                + getMaxDrivers(track) + ").");
        return JoinResult.JOINED;
    }

    public Duration getQualifyingTimeLeft() {
        if (qualifyingHeat == null || qualifyingHeat.getStartTime() == null
                || qualifyingHeat.getTimeLimit() == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(qualifyingHeat.getStartTime(), Instant.now());
        Duration left = Duration.ofMillis(qualifyingHeat.getTimeLimit()).minus(elapsed);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public void onDriverFinished(Driver driver) {
        if (graceTask != null) {
            return;
        }
        Heat heat = driver.getHeat();

        if (state == GrandPrixState.QUALIFYING && heat == qualifyingHeat) {
            logger.info(driver.getTPlayer().getName() + " completed their qualifying session, giving the rest of the "
                    + "field " + describe(config.qualifyingGrace()) + " to complete theirs.");
            startGracePeriod(heat, config.qualifyingGrace(), "Qualifying ends in");
        } else if (state == GrandPrixState.FINAL && heat == finalHeat) {
            logger.info(driver.getTPlayer().getName() + " won the Grand Prix, giving the rest of the field "
                    + describe(config.raceGrace()) + " to finish.");
            startGracePeriod(heat, config.raceGrace(), "Race ends in");
        }
    }

    public void onHeatFinished(Heat heat) {
        if (state == GrandPrixState.QUALIFYING && heat == qualifyingHeat) {
            cancelTasks();
            state = GrandPrixState.BETWEEN_HEATS;
            Announcer.broadcast(Palette.MUTED + "Qualifying has ended. The " + Palette.TEXT + finalHeat.getTotalLaps()
                    + Palette.MUTED + " lap final at " + Palette.TEXT + track.getDisplayName() + Palette.MUTED
                    + " is starting.");
            Bukkit.getScheduler().runTaskLater(plugin, this::startFinal, INTERMISSION_TICKS);
        } else if (state == GrandPrixState.FINAL && heat == finalHeat) {
            cancelTasks();
            state = GrandPrixState.BETWEEN_HEATS;
            Bukkit.getScheduler().runTaskLater(plugin, this::finishEvent, RESULTS_TICKS);
        }
    }

    private void startFinal() {
        if (state != GrandPrixState.BETWEEN_HEATS || finalHeat == null) {
            return;
        }

        if (!qualifyingHeat.getRound().finish(event)) {
            abort("the qualifying round could not be finished");
            return;
        }
        if (finalHeat.getDrivers().isEmpty()) {
            abort("nobody took part in qualifying");
            return;
        }
        if (!finalHeat.loadHeat()) {
            abort("the final could not be loaded");
            return;
        }

        state = GrandPrixState.FINAL;

        Heat heat = finalHeat;
        startCountdown(heat, "the final");

        Duration raceWatchdog = config.raceDuration().multipliedBy(RACE_WATCHDOG_FACTOR)
                .plus(config.raceGrace())
                .plus(WATCHDOG_HEADROOM);
        scheduleWatchdog(heat, raceWatchdog, "The final");
    }

    private void finishEvent() {
        if (event == null) {
            return;
        }

        if (!finalHeat.getRound().finish(event)) {
            logger.warning("Could not finish the final round of " + event.getDisplayName() + ".");
        }
        if (!event.finish()) {
            logger.warning("Could not finish the event " + event.getDisplayName() + ".");
        }

        recordResult();

        logger.info("Finished Grand Prix " + event.getDisplayName() + ".");
        deleteEvent();
        reset();
    }

    private void recordResult() {
        GrandPrixResult result = buildResult();
        results.setLatestResult(result);

        Optional<GrandPrixResult.Entry> winner = result.getWinner();
        if (winner.isPresent()) {
            statistics.recordWin(winner.get().playerId());
            statistics.endStreaksExcept(winner.get().playerId());
        } else {
            logger.info("No driver completed the Grand Prix distance, so no win was recorded.");
        }

        leaderboards.refresh();
    }

    private GrandPrixResult buildResult() {
        int totalLaps = finalHeat.getTotalLaps() == null ? 0 : finalHeat.getTotalLaps();

        List<GrandPrixResult.Entry> entries = finalHeat.getLivePositions().stream()
                .sorted(Comparator.comparingInt(Driver::getPosition))
                .map(driver -> new GrandPrixResult.Entry(
                        driver.getTPlayer().getUniqueId(),
                        driver.getTPlayer().getName(),
                        driver.getPosition(),
                        driver.getLaps().size(),
                        driver.getFinishTime(),
                        !driver.isDisqualified() && driver.getLaps().size() >= totalLaps))
                .toList();

        return new GrandPrixResult(track.getDisplayName(), totalLaps, System.currentTimeMillis(), entries);
    }

    private void startCountdown(Heat heat, String sessionName) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!heat.startCountdown(config.countdownSeconds())) {
                abort(sessionName + " could not be started");
            }
        }, GRID_SETTLE_TICKS);
    }

    private void startGracePeriod(Heat heat, Duration grace, String countdownLabel) {
        showCountdown(countdownLabel, grace);
        graceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            graceTask = null;
            forceFinish(heat);
        }, toTicks(grace));
    }

    private void showCountdown(String label, Duration length) {
        if (event == null || length.isZero()) {
            return;
        }
        try {
            event.getEventCountdown().startCountdown((int) length.toSeconds(), label);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to show the countdown boss bar", e);
        }
    }

    private void hideCountdown() {
        if (event == null) {
            return;
        }
        try {
            EventCountdown countdown = event.getEventCountdown();
            if (countdown.isActive()) {
                countdown.stopCountdown();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to hide the countdown boss bar", e);
        }
    }

    private void scheduleEmptySessionCheck() {
        Heat heat = qualifyingHeat;
        emptySessionTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            emptySessionTask = null;
            if (!heat.getDrivers().isEmpty() || heat.isFinished()) {
                return;
            }
            abort("nobody joined qualifying");
        }, toTicks(config.qualifyingDuration()));
    }

    private void scheduleWatchdog(Heat heat, Duration timeout, String sessionName) {
        watchdogTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            watchdogTask = null;
            if (heat.isFinished()) {
                return;
            }
            logger.warning(sessionName + " ran for longer than " + describe(timeout) + " without finishing "
                    + "and has been stopped.");
            if (!forceFinish(heat)) {
                abort(sessionName.toLowerCase(Locale.ROOT) + " could not be stopped");
            }
        }, toTicks(timeout));
    }

    private boolean forceFinish(Heat heat) {
        if (heat.isFinished()) {
            return true;
        }
        if (heat.finishHeat()) {
            return true;
        }
        logger.warning("Could not finish heat " + heat.getName() + " (state " + heat.getHeatState() + ").");
        return false;
    }

    public void abort(String reason) {
        logger.warning("Cancelling the Grand Prix: " + reason + ".");
        cancelTasks();

        state = GrandPrixState.IDLE;

        deleteEvent();
        reset();
    }

    public void shutdown() {
        cancelTasks();

        state = GrandPrixState.IDLE;

        if (event == null) {
            reset();
            return;
        }

        try {
            logger.info("Closing down the running Grand Prix " + event.getDisplayName() + ".");
            event.getRunningHeat().ifPresent(Heat::finishHeat);
            for (Round round : event.getEventSchedule().getRounds()) {
                if (round.getState() != Round.RoundState.FINISHED) {
                    round.finish(event);
                }
            }
            event.finish();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to close down the running Grand Prix", e);
        }

        deleteEvent();
        reset();
    }

    private void deleteEvent() {
        if (event == null) {
            return;
        }
        try {
            EventDatabase.removeEventHard(event);
            logger.info("Removed the event " + event.getDisplayName() + " from TimingSystem.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to remove the event from TimingSystem", e);
        }
    }

    private void cancelTasks() {
        hideCountdown();
        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }
        if (watchdogTask != null) {
            watchdogTask.cancel();
            watchdogTask = null;
        }
        if (emptySessionTask != null) {
            emptySessionTask.cancel();
            emptySessionTask = null;
        }
    }

    private void reset() {
        cancelTasks();
        state = GrandPrixState.IDLE;
        track = null;
        event = null;
        qualifyingHeat = null;
        finalHeat = null;
    }

    private int getMaxDrivers(Track track) {
        int gridSlots = track.getTrackLocations().getLocations(TrackLocation.Type.GRID).size();
        return Math.max(1, Math.min(config.maxDrivers(), gridSlots));
    }

    private void warnAboutMissingDrsRegions() {
        if (!config.drs()) {
            return;
        }
        if (track.getTrackRegions().getRegions(TrackRegion.RegionType.DRSDETECT).isEmpty()
                || track.getTrackRegions().getRegions(TrackRegion.RegionType.DRSACTIVATE).isEmpty()) {
            logger.info("DRS is enabled but " + track.getDisplayName() + " has no DRS detection and activation "
                    + "regions, so it will never open there.");
        }
    }

    private int getPits(Track track) {
        boolean hasPit = track.getTrackRegions().getRegion(TrackRegion.RegionType.PIT)
                .filter(TrackRegion::isDefined)
                .isPresent();
        return hasPit ? config.pits() : 0;
    }

    private String describeState() {
        return switch (state) {
            case IDLE -> "not scheduled";
            case SCHEDULED -> "waiting to start";
            case QUALIFYING -> "in qualifying";
            case BETWEEN_HEATS -> "between heats";
            case FINAL -> "in its final";
        };
    }

    private static long toTicks(Duration duration) {
        return Math.max(1L, duration.toSeconds() * 20L);
    }

    public static String describe(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds < 60) {
            return seconds + "s";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours == 0) {
            return minutes + "m";
        }
        return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }
}
