package com.github.M2B5.dailyGP.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class Announcer {

    private static final String PREFIX = Palette.ACCENT + Palette.BOLD + "» " + Palette.PRIMARY + Palette.BOLD
            + "DailyGP " + Palette.ACCENT + Palette.BOLD + "« " + Palette.RESET;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Announcer() {
    }

    public static void broadcast(String message) {
        Bukkit.broadcast(prefixed(message));
    }

    public static void broadcastClickable(String message, String command, String hover) {
        Bukkit.broadcast(clickable(prefixed(message), command, hover));
    }

    public static void broadcast(List<Component> lines) {
        for (Component line : lines) {
            Bukkit.broadcast(line);
        }
    }

    public static void send(CommandSender recipient, String message) {
        recipient.sendMessage(prefixed(message));
    }

    public static void sendRaw(CommandSender recipient, String message) {
        recipient.sendMessage(LEGACY.deserialize(message));
    }

    public static void send(CommandSender recipient, List<Component> lines) {
        for (Component line : lines) {
            recipient.sendMessage(line);
        }
    }

    public static Component text(String message) {
        return LEGACY.deserialize(message);
    }

    public static Component clickable(String text, String command, String hover) {
        return clickable(LEGACY.deserialize(text), command, hover);
    }

    private static Component clickable(Component component, String command, String hover) {
        return component
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(LEGACY.deserialize(hover)));
    }

    private static Component prefixed(String message) {
        return LEGACY.deserialize(PREFIX + message);
    }
}
