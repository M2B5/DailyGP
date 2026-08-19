package com.github.M2B5.dailyGP.grandprix;

import me.makkuusen.timing.system.TrackTagManager;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.tags.TrackTag;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;

public class TrackSelector {

    private final Logger logger;
    private final ExcludedTrackQueue excludedTracks;
    private final Random random = new Random();

    public TrackSelector(Logger logger, ExcludedTrackQueue excludedTracks) {
        this.logger = logger;
        this.excludedTracks = excludedTracks;
    }

    public Optional<Track> drawTrack(String tagName) {
        List<Track> pool = getPool(tagName);
        if (pool.isEmpty()) {
            return Optional.empty();
        }

        List<Track> available = pool.stream().filter(track -> !excludedTracks.isExcluded(track)).toList();
        if (available.isEmpty()) {
            logger.info("All " + pool.size() + " eligible track(s) have been raced recently, so the excluded track "
                    + "queue is being ignored for this draw. Tag more tracks, or lower "
                    + "grand-prix.excluded-track-queue-length, to keep the rotation varied.");
            available = pool;
        }

        Track drawnTrack = available.get(random.nextInt(available.size()));
        excludedTracks.add(drawnTrack);
        return Optional.of(drawnTrack);
    }

    public List<Track> getPool(String tagName) {
        TrackTag tag = TrackTagManager.getTrackTag(tagName.toUpperCase(Locale.ROOT));
        if (tag == null) {
            logger.warning("No TimingSystem track tag named '" + tagName + "' exists. "
                    + "Create it with /ts tag create " + tagName + " and add it to the Grand Prix tracks.");
            return List.of();
        }

        List<Track> pool = TimingSystemAPI.getTracks().stream()
                .filter(track -> track.getTrackTags().hasTag(tag))
                .filter(this::isRaceable)
                .toList();

        if (pool.isEmpty()) {
            logger.warning("No open track tagged '" + tagName + "' has both a start region and grid positions, "
                    + "so no Grand Prix track could be drawn.");
        }
        return pool;
    }

    private boolean isRaceable(Track track) {
        return track.isOpen()
                && track.getTrackRegions().getStart().isPresent()
                && !track.getTrackLocations().getLocations(TrackLocation.Type.GRID).isEmpty();
    }
}
