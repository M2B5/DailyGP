package com.github.M2B5.dailyGP.message;

import com.github.M2B5.dailyGP.config.DailyGPConfig;
import com.github.M2B5.dailyGP.grandprix.GrandPrixManager;
import com.github.M2B5.dailyGP.grandprix.GrandPrixState;
import com.github.M2B5.dailyGP.util.Announcer;
import com.github.M2B5.dailyGP.util.Palette;
import me.makkuusen.timing.system.track.Track;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GrandPrixCard {

    private static final int CARD_WIDTH = 200;

    private GrandPrixCard() {
    }

    public static List<Component> build(GrandPrixManager manager) {
        Optional<Track> track = manager.getTrack();
        GrandPrixState state = manager.getState();

        if (track.isEmpty() || (state != GrandPrixState.QUALIFYING && state != GrandPrixState.SCHEDULED)) {
            return List.of();
        }

        DailyGPConfig config = manager.getConfig();
        boolean open = state == GrandPrixState.QUALIFYING;

        List<Component> lines = new ArrayList<>();
        lines.add(Announcer.text(Palette.center(Palette.header("DAILY GRAND PRIX"), true, CARD_WIDTH)));
        lines.add(Announcer.text(Palette.divider(CARD_WIDTH)));
        lines.add(field("Track", Palette.TEXT + track.get().getDisplayName()));
        lines.add(field("Race", Palette.TEXT + manager.getRaceLaps() + " laps " + Palette.FAINT + "· "
                + Palette.TEXT + describePits(manager.getRacePits())));

        if (open) {
            lines.add(field("Qualifying", Palette.TEXT
                    + GrandPrixManager.describe(manager.getQualifyingTimeLeft()) + " left"));
            lines.add(field("Drivers", Palette.TEXT + manager.getDrivers().size() + Palette.FAINT + "/"
                    + Palette.TEXT + manager.getMaxDrivers()));
            lines.add(joinButton());
        } else {
            lines.add(field("Qualifying", Palette.TEXT + "opens at " + DailyGPConfig.format(config.startTime())));
        }

        lines.add(Announcer.text(Palette.divider(CARD_WIDTH)));
        return lines;
    }

    private static Component joinButton() {
        String label = Palette.ACCENT + Palette.BOLD + "[ " + Palette.PRIMARY + Palette.BOLD + "JOIN NOW "
                + Palette.ACCENT + Palette.BOLD + "]";
        return Announcer.text(Palette.padding(label, true, CARD_WIDTH))
                .append(Announcer.clickable(label, "/dailygp join", Palette.PRIMARY + "Click to join qualifying"));
    }

    private static Component field(String label, String value) {
        return Announcer.text(" " + Palette.MUTED + label + Palette.FAINT + ": " + value);
    }

    private static String describePits(int pits) {
        if (pits == 0) {
            return "No pits";
        }
        return pits + (pits == 1 ? " pit" : " pits");
    }
}
