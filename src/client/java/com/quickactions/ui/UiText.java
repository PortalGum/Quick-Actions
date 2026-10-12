package com.quickactions.ui;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** string fitting and formatting */
public final class UiText {
    private static final String ELLIPSIS = "…";
    private static final char SECTION = '\u00A7';

    private UiText() {
    }

    /** cut the end: "Long na…" */
    public static String fitEnd(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + ELLIPSIS) > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + ELLIPSIS;
    }

    public static String fitStart(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String s = text;
        while (s.length() > 1 && font.width(ELLIPSIS + s) > maxWidth) {
            s = s.substring(1);
        }
        return ELLIPSIS + s;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    public static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    public static String activeCodes(String text) {
        String color = "";
        StringBuilder styles = new StringBuilder();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != SECTION) continue;
            char code = Character.toLowerCase(text.charAt(i + 1));
            if ("0123456789abcdef".indexOf(code) >= 0) {
                color = String.valueOf(SECTION) + code;
                styles.setLength(0);
            } else if ("klmno".indexOf(code) >= 0) {
                styles.append(SECTION).append(code);
            } else if (code == 'r') {
                color = "";
                styles.setLength(0);
            }
            i++;
        }
        return color + styles;
    }

    public static List<String> wrapFormatted(Font font, String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        String carry = "";
        for (String line : wrap(font, text, maxWidth)) {
            String styled = carry + line;
            result.add(styled);
            carry = activeCodes(styled);
        }
        return result;
    }
}