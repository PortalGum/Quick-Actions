package com.quickactions.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RichText {
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int UNDERLINE = 4;
    public static final int STRIKETHROUGH = 8;
    public static final int OBFUSCATED = 16;

    private static final char SECTION = '\u00A7';
    private static final String CODES = "0123456789abcdef";
    private static final String[] NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    public record Run(String text, @Nullable String colorKey, int formats) {
        boolean sameStyle(Run other) {
            return formats == other.formats && java.util.Objects.equals(colorKey, other.colorKey);
        }
    }

    private RichText() {
    }

    // ---------- colors ----------

    public static int indexOfName(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) return i;
        }
        return -1;
    }

    public static char codeOfName(String name) {
        return CODES.charAt(indexOfName(name));
    }

    public static boolean isHexColor(@Nullable String value) {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') return false;
        for (int i = 1; i < 7; i++) {
            if (Character.digit(value.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    /** RGB of the color or null if default */
    public static @Nullable Integer rgbOf(@Nullable String colorKey) {
        if (colorKey == null) return null;
        if (isHexColor(colorKey)) return Integer.parseInt(colorKey.substring(1), 16);
        int index = indexOfName(colorKey);
        return index >= 0 ? RGB[index] : null;
    }

    private static int formatBit(char code) {
        return switch (Character.toLowerCase(code)) {
            case 'l' -> BOLD;
            case 'o' -> ITALIC;
            case 'n' -> UNDERLINE;
            case 'm' -> STRIKETHROUGH;
            case 'k' -> OBFUSCATED;
            default -> 0;
        };
    }

    public static String formatCodes(int formats) {
        StringBuilder out = new StringBuilder();
        if ((formats & BOLD) != 0) out.append(SECTION).append('l');
        if ((formats & ITALIC) != 0) out.append(SECTION).append('o');
        if ((formats & UNDERLINE) != 0) out.append(SECTION).append('n');
        if ((formats & STRIKETHROUGH) != 0) out.append(SECTION).append('m');
        if ((formats & OBFUSCATED) != 0) out.append(SECTION).append('k');
        return out.toString();
    }

    // ---------- parsing ----------

    public static List<Run> parse(String editable) {
        List<Run> runs = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        String color = null;
        int formats = 0;

        int i = 0;
        int length = editable.length();
        while (i < length) {
            char c = editable.charAt(i);

            if (c == '\\' && i + 1 < length && editable.charAt(i + 1) == 'n') {
                text.append('\n');
                i += 2;
                continue;
            }

            if (c == '&' && i + 1 < length) {
                char next = editable.charAt(i + 1);

                if (next == '&') {
                    text.append('&');
                    i += 2;
                    continue;
                }

                if (next == '#' && i + 8 <= length && isHexColor("#" + editable.substring(i + 2, i + 8))) {
                    flush(runs, text, color, formats);
                    color = "#" + editable.substring(i + 2, i + 8).toLowerCase(Locale.ROOT);
                    formats = 0;
                    i += 8;
                    continue;
                }

                int colorIndex = CODES.indexOf(Character.toLowerCase(next));
                if (colorIndex >= 0) {
                    flush(runs, text, color, formats);
                    color = NAMES[colorIndex];
                    formats = 0;
                    i += 2;
                    continue;
                }

                int bit = formatBit(next);
                if (bit != 0) {
                    flush(runs, text, color, formats);
                    formats |= bit;
                    i += 2;
                    continue;
                }

                if (next == 'r' || next == 'R') {
                    flush(runs, text, color, formats);
                    color = null;
                    formats = 0;
                    i += 2;
                    continue;
                }
            }

            text.append(c);
            i++;
        }
        flush(runs, text, color, formats);
        return merge(runs);
    }

    private static void flush(List<Run> runs, StringBuilder text, @Nullable String color, int formats) {
        if (text.length() > 0) {
            runs.add(new Run(text.toString(), color, formats));
            text.setLength(0);
        }
    }

    private static List<Run> merge(List<Run> runs) {
        List<Run> merged = new ArrayList<>();
        for (Run run : runs) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).sameStyle(run)) {
                Run last = merged.remove(merged.size() - 1);
                merged.add(new Run(last.text() + run.text(), run.colorKey(), run.formats()));
            } else {
                merged.add(run);
            }
        }
        return merged;
    }

    public static String toLegacy(List<Run> runs) {
        StringBuilder out = new StringBuilder();
        String emitted = "";
        for (Run run : runs) {
            StringBuilder codes = new StringBuilder();
            String color = run.colorKey();
            if (color != null && indexOfName(color) >= 0) {
                codes.append(SECTION).append(codeOfName(color));
            }
            codes.append(formatCodes(run.formats()));

            String value = codes.toString();
            if (!value.equals(emitted)) {
                if (!emitted.isEmpty()) {
                    out.append(SECTION).append('r'); // reset previous style
                }
                out.append(value);
                emitted = value;
            }
            out.append(run.text());
        }
        return out.toString();
    }

    // ---------- wrapping ----------

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n') {
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    private static void append(List<Run> line, String text, Run style) {
        if (!line.isEmpty() && line.get(line.size() - 1).sameStyle(style)) {
            Run last = line.remove(line.size() - 1);
            line.add(new Run(last.text() + text, style.colorKey(), style.formats()));
        } else {
            line.add(new Run(text, style.colorKey(), style.formats()));
        }
    }

    public static List<List<Run>> wrap(Font font, List<Run> runs, int maxWidth) {
        List<List<Run>> lines = new ArrayList<>();
        List<Run> line = new ArrayList<>();
        int lineWidth = 0;

        for (Run run : runs) {
            String prefix = formatCodes(run.formats());

            for (String token : tokenize(run.text())) {
                if (token.equals("\n")) {
                    lines.add(line);
                    line = new ArrayList<>();
                    lineWidth = 0;
                    continue;
                }

                if (token.equals(" ")) {
                    if (lineWidth == 0) continue; // no leading spaces
                    int width = font.width(prefix + " ");
                    if (lineWidth + width > maxWidth) { // wrap on space
                        lines.add(line);
                        line = new ArrayList<>();
                        lineWidth = 0;
                    } else {
                        append(line, " ", run);
                        lineWidth += width;
                    }
                    continue;
                }

                int width = font.width(prefix + token);
                if (lineWidth > 0 && lineWidth + width > maxWidth) {
                    lines.add(line);
                    line = new ArrayList<>();
                    lineWidth = 0;
                }
                if (width <= maxWidth) {
                    append(line, token, run);
                    lineWidth += width;
                    continue;
                }

                // word wider than line: cut by chars
                for (int codePoint : token.codePoints().toArray()) {
                    String character = new String(Character.toChars(codePoint));
                    int characterWidth = font.width(prefix + character);
                    if (lineWidth > 0 && lineWidth + characterWidth > maxWidth) {
                        lines.add(line);
                        line = new ArrayList<>();
                        lineWidth = 0;
                    }
                    append(line, character, run);
                    lineWidth += characterWidth;
                }
            }
        }
        lines.add(line);
        return lines;
    }

    // ---------- render ----------

    public static void draw(GuiGraphicsExtractor graphics, Font font, List<List<Run>> lines,
                            int x, int y, int maxLines, int defaultColor) {
        int step = font.lineHeight + 2;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            graphics.text(font, toComponent(lines.get(i)), x, y + i * step, defaultColor, true);
        }
    }

    public static MutableComponent toComponent(List<Run> line) {
        MutableComponent result = Component.empty();
        for (Run run : line) {
            result.append(Component.literal(run.text()).withStyle(styleOf(run)));
        }
        return result;
    }

    private static Style styleOf(Run run) {
        Style style = Style.EMPTY;
        Integer rgb = rgbOf(run.colorKey());
        if (rgb != null) style = style.withColor(TextColor.fromRgb(rgb));
        if ((run.formats() & BOLD) != 0) style = style.withBold(true);
        if ((run.formats() & ITALIC) != 0) style = style.withItalic(true);
        if ((run.formats() & UNDERLINE) != 0) style = style.withUnderlined(true);
        if ((run.formats() & STRIKETHROUGH) != 0) style = style.withStrikethrough(true);
        if ((run.formats() & OBFUSCATED) != 0) style = style.withObfuscated(true);
        return style;
    }
}