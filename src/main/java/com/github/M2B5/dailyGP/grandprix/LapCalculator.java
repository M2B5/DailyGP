package com.github.M2B5.dailyGP.grandprix;

import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.track.Track;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public final class LapCalculator {

    private static final int REFERENCE_TIME_POSITION = 5;

    private LapCalculator() {
    }

    public static int calculateLaps(Track track, Duration raceDuration, double paceMultiplier, Logger logger) {
        if (track.isStage()) {
            return 1;
        }

        long referenceLapMillis = getReferenceLapMillis(track);
        if (referenceLapMillis <= 0) {
            int fallback = Math.max(1, TimingSystem.configuration.getLaps());
            logger.warning("No time trial time has been set on " + track.getDisplayName()
                    + ", so its length is unknown. Falling back to TimingSystem's default of " + fallback + " laps.");
            return fallback;
        }

        long expectedLapMillis = Math.round(referenceLapMillis * paceMultiplier);
        long laps = Math.round((double) raceDuration.toMillis() / expectedLapMillis);
        return (int) Math.max(1, laps);
    }

    private static long getReferenceLapMillis(Track track) {
        List<TimeTrialFinish> topList = track.getTimeTrials().getTopList(REFERENCE_TIME_POSITION);
        return topList.isEmpty() ? -1 : topList.get(topList.size() - 1).getTime();
    }
}
