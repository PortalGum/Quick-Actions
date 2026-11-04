package com.quickactions.ui.theme;

import com.quickactions.config.QuickActionsConfig;

import java.util.ArrayList;
import java.util.List;

public final class ThemePresets {
    public static final int MAX_USER_PRESETS = 24;
    public static final int NAME_MAX_LENGTH = 24;

    public record BuiltIn(String id, int surface, int accent) {
    }

    public static final class User {
        public String name = "";
        public int surface = Theme.DEFAULT_SURFACE;
        public int accent = Theme.DEFAULT_ACCENT;

        public User() {
        }

        public User(String name, int surface, int accent) {
            this.name = name;
            this.surface = surface & 0xFFFFFF;
            this.accent = accent & 0xFFFFFF;
        }
    }

    public static final List<BuiltIn> BUILT_IN = List.of(
            new BuiltIn("classic", 0x121620, 0x4C8DFF),
            new BuiltIn("midnight", 0x0C0E1E, 0x7B83FF),
            new BuiltIn("ocean", 0x0E1A22, 0x27C7E6),
            new BuiltIn("emerald", 0x0F1B17, 0x2FCB86),
            new BuiltIn("lime", 0x131B10, 0x9BE24F),
            new BuiltIn("amber", 0x1C1710, 0xFFB22E),
            new BuiltIn("sunset", 0x1F1412, 0xFF7B45),
            new BuiltIn("crimson", 0x1B1113, 0xFF4D5F),
            new BuiltIn("rose", 0x1F121B, 0xFF6FB7),
            new BuiltIn("violet", 0x18132B, 0xA36BFF),
            new BuiltIn("graphite", 0x171718, 0x9AA3B8),
            new BuiltIn("mono", 0x0F0F10, 0xE3E6EC)
    );

    private ThemePresets() {
    }

    public static List<User> user() {
        QuickActionsConfig cfg = QuickActionsConfig.get();
        if (cfg.userThemes == null) cfg.userThemes = new ArrayList<>();
        return cfg.userThemes;
    }

    public static boolean saveUser(String name, int surface, int accent) {
        List<User> list = user();
        String clean = name.trim();
        if (clean.length() > NAME_MAX_LENGTH) clean = clean.substring(0, NAME_MAX_LENGTH);
        if (clean.isEmpty()) return false;
        for (User existing : list) {
            if (existing.name.equalsIgnoreCase(clean)) {
                existing.surface = surface & 0xFFFFFF;
                existing.accent = accent & 0xFFFFFF;
                QuickActionsConfig.save();
                return true;
            }
        }
        if (list.size() >= MAX_USER_PRESETS) return false;
        list.add(new User(clean, surface, accent));
        QuickActionsConfig.save();
        return true;
    }

    public static void deleteUser(User preset) {
        user().remove(preset);
        QuickActionsConfig.save();
    }

    public static boolean sameColors(int surfaceA, int accentA, int surfaceB, int accentB) {
        return Theme.normalizeSurface(surfaceA) == Theme.normalizeSurface(surfaceB)
                && Theme.normalizeAccent(accentA) == Theme.normalizeAccent(accentB);
    }

    public static int[] random() {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        float hue = rnd.nextFloat();
        float surfaceHue = (hue + (rnd.nextBoolean() ? 0f : (rnd.nextFloat() - 0.5f) * 0.12f) + 1f) % 1f;
        int accent = Colors.fromHsv(hue, 0.55f + rnd.nextFloat() * 0.40f, 0.85f + rnd.nextFloat() * 0.15f);
        int surface = Colors.fromHsv(surfaceHue, 0.25f + rnd.nextFloat() * 0.35f, 0.08f + rnd.nextFloat() * 0.07f);
        return new int[]{surface & 0xFFFFFF, accent & 0xFFFFFF};
    }
}
