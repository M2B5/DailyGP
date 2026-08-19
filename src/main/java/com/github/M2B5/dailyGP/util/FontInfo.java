package com.github.M2B5.dailyGP.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A utility class to calculate the pixel width of strings based on Minecraft's default font.
 * Widths are based on the values from the Minecraft Wiki and include the 1px spacing between characters.
 * Bold characters are 1px wider than their normal counterparts.
 *
 * @see <a href="https://minecraft.fandom.com/wiki/Character_width">Minecraft Wiki: Character Width</a>
 */
public final class FontInfo {

    private static final Map<Character, Integer> CHAR_WIDTHS = new HashMap<>();
    /** The width of a space character in pixels. */
    public static final int SPACE_WIDTH = 4;

    static {
        CHAR_WIDTHS.put('!', 2); CHAR_WIDTHS.put('"', 4); CHAR_WIDTHS.put('\'', 3);
        CHAR_WIDTHS.put('(', 4); CHAR_WIDTHS.put(')', 4); CHAR_WIDTHS.put('*', 4);
        CHAR_WIDTHS.put(',', 2); CHAR_WIDTHS.put('-', 6); CHAR_WIDTHS.put('.', 2);
        CHAR_WIDTHS.put('/', 6); CHAR_WIDTHS.put('0', 6); CHAR_WIDTHS.put('1', 6);
        CHAR_WIDTHS.put('2', 6); CHAR_WIDTHS.put('3', 6); CHAR_WIDTHS.put('4', 6);
        CHAR_WIDTHS.put('5', 6); CHAR_WIDTHS.put('6', 6); CHAR_WIDTHS.put('7', 6);
        CHAR_WIDTHS.put('8', 6); CHAR_WIDTHS.put('9', 6); CHAR_WIDTHS.put(':', 2);
        CHAR_WIDTHS.put(';', 2); CHAR_WIDTHS.put('<', 5); CHAR_WIDTHS.put('=', 6);
        CHAR_WIDTHS.put('>', 5); CHAR_WIDTHS.put('?', 6); CHAR_WIDTHS.put('@', 7);
        CHAR_WIDTHS.put('A', 6); CHAR_WIDTHS.put('B', 6); CHAR_WIDTHS.put('C', 6);
        CHAR_WIDTHS.put('D', 6); CHAR_WIDTHS.put('E', 6); CHAR_WIDTHS.put('F', 6);
        CHAR_WIDTHS.put('G', 6); CHAR_WIDTHS.put('H', 6); CHAR_WIDTHS.put('I', 4);
        CHAR_WIDTHS.put('J', 6); CHAR_WIDTHS.put('K', 6); CHAR_WIDTHS.put('L', 6);
        CHAR_WIDTHS.put('M', 6); CHAR_WIDTHS.put('N', 6); CHAR_WIDTHS.put('O', 6);
        CHAR_WIDTHS.put('P', 6); CHAR_WIDTHS.put('Q', 6); CHAR_WIDTHS.put('R', 6);
        CHAR_WIDTHS.put('S', 6); CHAR_WIDTHS.put('T', 6); CHAR_WIDTHS.put('U', 6);
        CHAR_WIDTHS.put('V', 6); CHAR_WIDTHS.put('W', 6); CHAR_WIDTHS.put('X', 6);
        CHAR_WIDTHS.put('Y', 6); CHAR_WIDTHS.put('Z', 6); CHAR_WIDTHS.put('[', 4);
        CHAR_WIDTHS.put('\\', 6);CHAR_WIDTHS.put(']', 4); CHAR_WIDTHS.put('^', 6);
        CHAR_WIDTHS.put('_', 6); CHAR_WIDTHS.put('`', 3); CHAR_WIDTHS.put('a', 6);
        CHAR_WIDTHS.put('b', 6); CHAR_WIDTHS.put('c', 6); CHAR_WIDTHS.put('d', 6);
        CHAR_WIDTHS.put('e', 6); CHAR_WIDTHS.put('f', 5); CHAR_WIDTHS.put('g', 6);
        CHAR_WIDTHS.put('h', 6); CHAR_WIDTHS.put('i', 2); CHAR_WIDTHS.put('j', 6);
        CHAR_WIDTHS.put('k', 5); CHAR_WIDTHS.put('l', 3); CHAR_WIDTHS.put('m', 6);
        CHAR_WIDTHS.put('n', 6); CHAR_WIDTHS.put('o', 6); CHAR_WIDTHS.put('p', 6);
        CHAR_WIDTHS.put('q', 6); CHAR_WIDTHS.put('r', 6); CHAR_WIDTHS.put('s', 6);
        CHAR_WIDTHS.put('t', 4); CHAR_WIDTHS.put('u', 6); CHAR_WIDTHS.put('v', 6);
        CHAR_WIDTHS.put('w', 6); CHAR_WIDTHS.put('x', 6); CHAR_WIDTHS.put('y', 6);
        CHAR_WIDTHS.put('z', 6); CHAR_WIDTHS.put('{', 4); CHAR_WIDTHS.put('|', 2);
        CHAR_WIDTHS.put('}', 4); CHAR_WIDTHS.put('~', 7); CHAR_WIDTHS.put(' ', SPACE_WIDTH);
    }

    private FontInfo() {}

    /**
     * Calculates the pixel width of a string, skipping Minecraft color/format codes (§x).
     *
     * @param text The string to measure (may contain §-codes).
     * @param bold Whether the text is rendered bold. Bold characters are 1px wider.
     * @return The total pixel width of the visible characters.
     */
    public static int getStringWidth(String text, boolean bold) {
        if (text == null || text.isEmpty()) return 0;

        int width = 0;
        boolean skipNext = false;
        for (char c : text.toCharArray()) {
            if (skipNext) { skipNext = false; continue; }
            if (c == '§') { skipNext = true; continue; }
            int charWidth = CHAR_WIDTHS.getOrDefault(c, 6);
            width += charWidth;
            if (bold && c != ' ') width++;
        }
        return width;
    }

    /**
     * Returns a string of spaces whose total pixel width is as close as possible to
     * {@code targetPixels - currentPixels} without exceeding it.
     *
     * @param currentWidth  Pixel width already consumed.
     * @param targetWidth   Pixel width to reach.
     * @return A string of spaces (may be empty if already at or past target).
     */
    public static String spacesTo(int currentWidth, int targetWidth) {
        int remaining = targetWidth - currentWidth;
        if (remaining <= 0) return "";
        return " ".repeat(remaining / SPACE_WIDTH);
    }
}
