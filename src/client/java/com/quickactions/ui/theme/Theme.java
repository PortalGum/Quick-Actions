package com.quickactions.ui.theme;

import com.quickactions.config.QuickActionsConfig;

public final class Theme {
    public static final int DEFAULT_SURFACE = 0x121620;
    public static final int DEFAULT_ACCENT = 0x4C8DFF;

    public static final float SURFACE_V_MIN = 0.035f;
    public static final float SURFACE_V_MAX = 0.30f;
    public static final float ACCENT_V_MIN = 0.40f;

    public static final class Palette {
        public final int surface;
        public final int accent;
        private final float hue;
        private final float sat;
        private final float val;

        public final int panelBg;
        public final int panelOpaque;
        public final int deep;
        public final int card;
        public final int cardHover;
        public final int tile;
        public final int borderSoft;
        public final int border;
        public final int borderStrong;
        public final int scroll;
        public final int subtle;
        public final int muted;
        public final int textSoft;
        public final int text;
        public final int textBright;
        public final int accentSoft;
        public final int accentDeep;
        public final int accentLight;
        public final int accentPale;
        public final int accentTile;
        public final int accentSelected;
        public final int tooltipBg;
        public final int ghostBg;

        private Palette(int surfaceRgb, int accentRgb) {
            this.surface = 0xFF000000 | normalizeSurface(surfaceRgb);
            this.accent = 0xFF000000 | normalizeAccent(accentRgb);

            float[] hsv = Colors.toHsv(surface);
            this.hue = hsv[0];
            this.sat = hsv[1];
            this.val = hsv[2];

            this.panelOpaque = surface;
            this.panelBg = Colors.withAlpha(surface, 0xF0);
            this.deep = Colors.mix(surface, 0xFF000000, 0.40f);
            this.card = tone(0.040f);
            this.cardHover = tone(0.099f);
            this.tile = tone(0.070f);
            this.borderSoft = tone(0.108f);
            this.border = tone(0.170f);
            this.borderStrong = tone(0.287f);
            this.scroll = tone(0.358f);
            this.subtle = tone(0.668f);
            this.muted = tone(0.610f);
            this.textSoft = tone(0.789f);
            this.text = tone(0.874f);
            this.textBright = tone(0.955f);
            this.tooltipBg = Colors.withAlpha(Colors.mix(surface, 0xFF000000, 0.12f), 0xF0);
            this.ghostBg = Colors.withAlpha(tone(0.050f), 0xE6);

            // accent shades
            this.accentSoft = Colors.mix(surface, accent, 0.57f);
            this.accentTile = Colors.mix(surface, accent, 0.30f);
            this.accentSelected = Colors.mix(surface, accent, 0.36f);
            this.accentDeep = darkenForWhiteText(accent);
            this.accentLight = lightenForDarkBackground(accent, 0.35f, 0.38);
            this.accentPale = lightenForDarkBackground(accent, 0.50, 0.50);
        }

        public static Palette of(int surfaceRgb, int accentRgb) {
            return new Palette(surfaceRgb, accentRgb);
        }

        private static final float[][] SAT_CURVE = {
                {0f, 1f}, {0.06f, 1f}, {0.11f, 0.9f}, {0.17f, 0.78f}, {0.36f, 0.77f},
                {0.61f, 0.39f}, {0.67f, 0.34f}, {0.79f, 0.26f}, {0.87f, 0.25f}, {0.955f, 0.14f}, {1f, 0.10f}
        };

        private static float satFactor(float t) {
            for (int i = 1; i < SAT_CURVE.length; i++) {
                if (t <= SAT_CURVE[i][0]) {
                    float a = SAT_CURVE[i - 1][0];
                    float b = SAT_CURVE[i][0];
                    float k = b <= a ? 0f : (t - a) / (b - a);
                    return SAT_CURVE[i - 1][1] + (SAT_CURVE[i][1] - SAT_CURVE[i - 1][1]) * k;
                }
            }
            return SAT_CURVE[SAT_CURVE.length - 1][1];
        }

        public int tone(float t) {
            float k = Colors.clamp01(t);
            return Colors.fromHsv(hue, sat * satFactor(k), val + (1f - val) * k);
        }

        /** mix of bg and accent: 0 = bg, 1 = accent */
        public int accentMix(float t) {
            return Colors.mix(surface, accent, t);
        }

        public int accentAlpha(int alpha) {
            return Colors.withAlpha(accent, alpha);
        }

        public int surfaceAlpha(int alpha) {
            return Colors.withAlpha(surface, alpha);
        }
    }

    public static int normalizeSurface(int rgb) {
        float[] hsv = Colors.toHsv(rgb);
        float v = Math.max(SURFACE_V_MIN, Math.min(SURFACE_V_MAX, hsv[2]));
        return Colors.fromHsv(hsv[0], hsv[1], v) & 0xFFFFFF;
    }

    public static int normalizeAccent(int rgb) {
        int color = 0xFF000000 | rgb;
        for (int i = 0; i < 20 && Colors.luminance(color) < 0.12; i++) {
            color = Colors.mix(color, 0xFFFFFFFF, 0.06f);
        }
        return color & 0xFFFFFF;
    }

    private static int darkenForWhiteText(int accent) {
        int color = Colors.mix(accent, 0xFF000000, 0.25f);
        for (int i = 0; i < 20 && Colors.luminance(color) > 0.19; i++) {
            color = Colors.mix(color, 0xFF000000, 0.08f);
        }
        return color;
    }

    private static int lightenForDarkBackground(int accent, double startMix, double minLuminance) {
        float t = (float) startMix;
        int color = Colors.mix(accent, 0xFFFFFFFF, t);
        for (int i = 0; i < 20 && Colors.luminance(color) < minLuminance; i++) {
            t += 0.05f;
            color = Colors.mix(accent, 0xFFFFFFFF, t);
        }
        return color;
    }

    // ---------- current theme ----------

    private static volatile Palette current = Palette.of(DEFAULT_SURFACE, DEFAULT_ACCENT);

    private Theme() {
    }

    public static Palette get() {
        return current;
    }

    public static void set(int surfaceRgb, int accentRgb) {
        current = Palette.of(surfaceRgb, accentRgb);
    }

    public static void sync() {
        QuickActionsConfig cfg = QuickActionsConfig.get();
        set(cfg.themeSurface, cfg.themeAccent);
    }

    public static void saveToConfig(int surfaceRgb, int accentRgb) {
        QuickActionsConfig cfg = QuickActionsConfig.get();
        cfg.themeSurface = surfaceRgb & 0xFFFFFF;
        cfg.themeAccent = accentRgb & 0xFFFFFF;
        QuickActionsConfig.save();
    }

    // ---------- short getters (ARGB) ----------

    public static int surface() { return current.surface; }
    public static int panelBg() { return current.panelBg; }
    public static int panelOpaque() { return current.panelOpaque; }
    public static int deep() { return current.deep; }
    public static int card() { return current.card; }
    public static int cardHover() { return current.cardHover; }
    public static int tile() { return current.tile; }
    public static int borderSoft() { return current.borderSoft; }
    public static int border() { return current.border; }
    public static int borderStrong() { return current.borderStrong; }
    public static int scroll() { return current.scroll; }
    public static int subtle() { return current.subtle; }
    public static int muted() { return current.muted; }
    public static int textSoft() { return current.textSoft; }
    public static int text() { return current.text; }
    public static int textBright() { return current.textBright; }
    public static int accent() { return current.accent; }
    public static int accentSoft() { return current.accentSoft; }
    public static int accentDeep() { return current.accentDeep; }
    public static int accentLight() { return current.accentLight; }
    public static int accentPale() { return current.accentPale; }
    public static int accentTile() { return current.accentTile; }
    public static int accentSelected() { return current.accentSelected; }
    public static int tooltipBg() { return current.tooltipBg; }
    public static int ghostBg() { return current.ghostBg; }
    public static int accentAlpha(int alpha) { return current.accentAlpha(alpha); }
    public static int surfaceAlpha(int alpha) { return current.surfaceAlpha(alpha); }
    public static int tone(float t) { return current.tone(t); }
    public static int accentMix(float t) { return current.accentMix(t); }
}
