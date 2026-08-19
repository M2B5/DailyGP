package com.github.M2B5.dailyGP.command;

import com.github.M2B5.dailyGP.DailyGP;
import com.github.M2B5.dailyGP.config.DailyGPConfig;
import com.github.M2B5.dailyGP.grandprix.GrandPrixManager;
import com.github.M2B5.dailyGP.grandprix.GrandPrixState;
import com.github.M2B5.dailyGP.message.GrandPrixCard;
import com.github.M2B5.dailyGP.util.Announcer;
import com.github.M2B5.dailyGP.util.Palette;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.track.Track;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class DailyGPCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "dailygp.admin";

    private static final List<String> PLAYER_SUBCOMMANDS = List.of("info", "join", "drivers");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("now", "reload", "selecttrack", "start", "cancel");

    private final DailyGP plugin;
    private final GrandPrixManager manager;

    public DailyGPCommand(DailyGP plugin, GrandPrixManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String @NotNull [] args) {
        String subcommand = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);

        return switch (subcommand) {
            case "info" -> showInfo(sender);
            case "join" -> join(sender);
            case "drivers" -> listDrivers(sender);
            case "now" -> !requireAdmin(sender) || startNow(sender);
            case "reload" -> !requireAdmin(sender) || reload(sender);
            case "selecttrack" -> !requireAdmin(sender) || selectTrack(sender);
            case "start" -> !requireAdmin(sender) || start(sender);
            case "cancel" -> !requireAdmin(sender) || cancel(sender);
            default -> showUsage(sender);
        };
    }

    private boolean showInfo(CommandSender sender) {
        DailyGPConfig config = manager.getConfig();
        Optional<Track> track = manager.getTrack();
        GrandPrixState state = manager.getState();

        List<Component> card = GrandPrixCard.build(manager);
        if (!card.isEmpty()) {
            Announcer.send(sender, card);
            return true;
        }

        Announcer.sendRaw(sender, Palette.header("DAILY GRAND PRIX"));
        switch (state) {
            case BETWEEN_HEATS -> Announcer.sendRaw(sender, Palette.MUTED
                    + "Qualifying finished, loading grid.");
            case FINAL -> Announcer.sendRaw(sender, Palette.MUTED + "Racing on " + Palette.TEXT
                    + track.map(Track::getDisplayName).orElse("unknown") + Palette.MUTED + " Laps: " + Palette.TEXT
                    + manager.getRaceLaps());
            default -> Announcer.sendRaw(sender, Palette.MUTED + "No Grand Prix is scheduled. Today's track is drawn at "
                    + Palette.TEXT + DailyGPConfig.format(config.trackSelectionTime()) + Palette.MUTED + ", in "
                    + GrandPrixManager.describe(timeUntil(config.trackSelectionTime())) + ".");
        }
        return true;
    }

    private boolean join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        switch (manager.join(player)) {
            case JOINED -> Announcer.send(player, Palette.TEXT + "You are in. " + Palette.MUTED + "You have "
                    + Palette.TEXT + GrandPrixManager.describe(manager.getQualifyingTimeLeft()) + Palette.MUTED
                    + " of qualifying left - drive through the start to begin your session.");
            case ALREADY_RACING -> Announcer.send(player, Palette.MUTED
                    + "You are already in this qualifying session.");
            case FULL -> Announcer.send(player, Palette.PRIMARY + "Qualifying is full.");
            case IN_ANOTHER_HEAT -> Announcer.send(player, Palette.PRIMARY + "You are already racing in another heat.");
            case CLOSING -> Announcer.send(player, Palette.PRIMARY
                    + "Qualifying is closed.");
            case FAILED -> Announcer.send(player, Palette.PRIMARY + "You could not be added to qualifying. "
                    + "Check the console for details.");
            case NOT_OPEN -> Announcer.send(player, describeWhenOpen());
        }
        return true;
    }

    private String describeWhenOpen() {
        return switch (manager.getState()) {
            case SCHEDULED -> Palette.PRIMARY + "Qualifying is not open yet. " + Palette.MUTED + "It opens at "
                    + Palette.TEXT + DailyGPConfig.format(manager.getConfig().startTime()) + Palette.MUTED + ", in "
                    + GrandPrixManager.describe(timeUntil(manager.getConfig().startTime())) + ".";
            case BETWEEN_HEATS, FINAL -> Palette.PRIMARY + "Qualifying is over.";
            default -> Palette.PRIMARY + "No Grand Prix is running. " + Palette.MUTED + "Today's track is drawn at "
                    + Palette.TEXT + DailyGPConfig.format(manager.getConfig().trackSelectionTime()) + Palette.MUTED
                    + ".";
        };
    }

    private boolean listDrivers(CommandSender sender) {
        List<Driver> drivers = manager.getDrivers();
        if (drivers.isEmpty()) {
            Announcer.send(sender, Palette.MUTED + "Nobody is in the Grand Prix yet.");
            return true;
        }

        Announcer.sendRaw(sender, Palette.header("DRIVERS") + Palette.FAINT + " (" + drivers.size() + "/"
                + manager.getMaxDrivers() + ")");
        int position = 1;
        for (Driver driver : drivers) {
            Announcer.sendRaw(sender, Palette.FAINT + " " + position++ + ". " + Palette.TEXT
                    + driver.getTPlayer().getName());
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reload();
        Announcer.send(sender, Palette.TEXT + "Configuration reloaded.");
        return true;
    }

    private boolean selectTrack(CommandSender sender) {
        if (manager.getState() != GrandPrixState.IDLE) {
            Announcer.send(sender, Palette.PRIMARY + "A Grand Prix has already been set up. " + Palette.MUTED
                    + "Cancel it first with " + Palette.ACCENT + "/dailygp cancel" + Palette.MUTED + ".");
            return true;
        }
        if (!manager.selectTrack()) {
            Announcer.send(sender, Palette.PRIMARY + "No track could be drawn. " + Palette.MUTED
                    + "Check the console for details.");
        }
        return true;
    }

    private boolean start(CommandSender sender) {
        if (manager.isRunning()) {
            Announcer.send(sender, Palette.PRIMARY + "The Grand Prix is already running.");
            return true;
        }
        if (!manager.start()) {
            Announcer.send(sender, Palette.PRIMARY + "The Grand Prix could not be started. " + Palette.MUTED
                    + "Check the console for details.");
        }
        return true;
    }

    private boolean startNow(CommandSender sender) {
        if (manager.isRunning()) {
            Announcer.send(sender, Palette.PRIMARY + "A Grand Prix is already running. " + Palette.MUTED
                    + "Cancel it first with " + Palette.ACCENT + "/dailygp cancel" + Palette.MUTED + ".");
            return true;
        }

        if (manager.startNow()) {
            Announcer.send(sender, Palette.TEXT + "Grand Prix called. " + Palette.MUTED + "Qualifying is open.");
        } else {
            Announcer.send(sender, Palette.PRIMARY + "The Grand Prix could not be called. " + Palette.MUTED
                    + "Check the console for details.");
        }
        return true;
    }

    private boolean cancel(CommandSender sender) {
        if (manager.getState() == GrandPrixState.IDLE) {
            Announcer.send(sender, Palette.PRIMARY + "There is no Grand Prix to cancel.");
            return true;
        }
        manager.abort("cancelled by " + sender.getName());
        Announcer.send(sender, Palette.TEXT + "Grand Prix cancelled.");
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        Announcer.send(sender, Palette.PRIMARY + "You don't have permission to do that.");
        return false;
    }

    private boolean showUsage(CommandSender sender) {
        Announcer.sendRaw(sender, Palette.header("DAILYGP"));
        usageLine(sender, "/dailygp info", "Show the current Grand Prix status");
        usageLine(sender, "/dailygp join", "Join the running qualifying session");
        usageLine(sender, "/dailygp drivers", "List the drivers taking part");
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            usageLine(sender, "/dailygp now", "Call a Grand Prix on a fresh track right now");
            usageLine(sender, "/dailygp reload", "Reload the configuration");
            usageLine(sender, "/dailygp selecttrack", "Draw today's track now");
            usageLine(sender, "/dailygp start", "Open qualifying for the scheduled Grand Prix now");
            usageLine(sender, "/dailygp cancel", "Cancel the current Grand Prix");
        }
        return true;
    }

    private void usageLine(CommandSender sender, String command, String description) {
        Announcer.sendRaw(sender, " " + Palette.ACCENT + command + Palette.FAINT + " - " + Palette.MUTED
                + description);
    }

    private List<String> filterByPrefix(List<String> options, String argument) {
        String prefix = argument.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private Duration timeUntil(LocalTime timeOfDay) {
        LocalDateTime now = LocalDateTime.now(manager.getConfig().timezone());
        LocalDateTime next = now.toLocalDate().atTime(timeOfDay);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.ofMinutes(Duration.between(now, next).toMinutes());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>(PLAYER_SUBCOMMANDS);
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            options.addAll(ADMIN_SUBCOMMANDS);
        }
        return filterByPrefix(options, args[0]);
    }
}
