package com.github.M2B5.dailyGP.util;

public final class Palette {
    public static final String PRIMARY = "§c";
    public static final String ACCENT = "§b";
    public static final String TEXT = "§f";
    public static final String MUTED = "§7";
    public static final String FAINT = "§8";
    public static final String BOLD = "§l";
    public static final String RESET = "§r";

    private Palette() {
    }

    public static String header(String title) {
        return ACCENT + BOLD + "» " + PRIMARY + BOLD + title + " " + ACCENT + BOLD + "«";
    }

    public static String divider(int width) {
        int dashWidth = FontInfo.getStringWidth("-", false);
        return FAINT + "§m" + "-".repeat(Math.max(1, width / dashWidth));
    }

    public static String center(String text, boolean bold, int width) {
        return padding(text, bold, width) + text;
    }

    public static String padding(String text, boolean bold, int width) {
        int leadingPixels = Math.max(0, (width - FontInfo.getStringWidth(text, bold)) / 2);
        return " ".repeat(leadingPixels / FontInfo.SPACE_WIDTH);
    }
}
