package com.quickactions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.quickactions.QuickActionsClient;
import com.quickactions.ui.theme.Theme;
import com.quickactions.ui.theme.ThemePresets;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class QuickActionsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("quickactions.json");
    private static QuickActionsConfig instance = new QuickActionsConfig();

    public boolean enabled = true;
    public boolean showInWorldSelection = true;
    public boolean showInServerList = true;
    public boolean showInResourcePacks = true;
    public boolean showInScreenshotViewer = true;
    public boolean serverDragReorder = true;
    public boolean packDragReorder = true;
    public boolean showScreenshotButton = true;
    public boolean checkForUpdates = true;
    public int themeSurface = Theme.DEFAULT_SURFACE;
    public int themeAccent = Theme.DEFAULT_ACCENT;
    public List<ThemePresets.User> userThemes = new ArrayList<>();

    public static QuickActionsConfig get() {
        return instance;
    }

    /** reset all settings to defaults and save */
    public static void reset() {
        List<ThemePresets.User> keep = instance.userThemes; // custom theme presets are not reset
        instance = new QuickActionsConfig();
        if (keep != null) instance.userThemes = keep;
        Theme.sync();
        save();
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                QuickActionsConfig loaded = GSON.fromJson(reader, QuickActionsConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | JsonParseException e) {
                QuickActionsClient.LOGGER.error("Failed to read config, using defaults", e);
            }
        }
        Theme.sync();
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to save config", e);
        }
    }
}