package com.quickactions.ui.theme;

import java.util.Locale;

public final class Colors {
    private Colors() {
    }

    public static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** RGB (low 24 bits) -> {h, s, v}, all 0..1 */
    public static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d < 1e-6f) {
            h = 0f;
        } else if (max == r) {
            h = ((g - b) / d) % 6f;
        } else if (max == g) {
            h = (b - r) / d + 2f;
        } else {
            h = (r - g) / d + 4f;
        }
        h /= 6f;
        if (h < 0f) h += 1f;
        float s = max < 1e-6f ? 0f : d / max;
        return new float[]{h, s, max};
    }

    /** h, s, v in 0..1 -> opaque ARGB */
    public static int fromHsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = clamp01(s);
        v = clamp01(v);
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r;
        float g;
        float b;
        int sector = (int) (h * 6f);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return 0xFF000000
                | (Math.round((r + m) * 255f) << 16)
                | (Math.round((g + m) * 255f) << 8)
                | Math.round((b + m) * 255f);
    }

    public static int mix(int from, int to, float t) {
        float k = clamp01(t);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int mixArgb(int from, int to, float t) {
        float k = clamp01(t);
        int a = Math.round(((from >>> 24)) * (1 - k) + ((to >>> 24)) * k);
        return (a << 24) | (mix(from, to, k) & 0x00FFFFFF);
    }

    /** relative luminance by WCAG, 0..1 */
    public static double luminance(int rgb) {
        return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF)
                + 0.0722 * channel(rgb & 0xFF);
    }

    private static double channel(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static int parseHex(String text) {
        if (text == null) return -1;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) sb.append(c).append(c);
            s = sb.toString();
        }
        if (s.length() != 6) return -1;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
