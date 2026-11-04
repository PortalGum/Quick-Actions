package com.quickactions.actions.pack.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quickactions.QuickActionsClient;
import com.quickactions.actions.pack.preset.PackPreset.PresetPack;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** one json file per preset */
public final class PresetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private record Target(String name, Path file) {
    }

    private PresetStore() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("quickactions").resolve("pack_presets");
    }

    // ---------- read ----------

    public static List<PackPreset> list() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) return List.of();

        List<PackPreset> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : stream.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                try {
                    result.add(read(file));
                } catch (Exception e) {
                    QuickActionsClient.LOGGER.warn("Skipping invalid preset {}", file, e);
                }
            }
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to list presets", e);
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    private static PackPreset read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            String fileName = file.getFileName().toString();
            String name = root.has("name") ? root.get("name").getAsString()
                    : fileName.substring(0, fileName.length() - ".json".length());
            long saved = root.has("saved") ? root.get("saved").getAsLong()
                    : Files.getLastModifiedTime(file).toMillis();

            List<PresetPack> packs = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("packs")) {
                JsonObject object = element.getAsJsonObject();
                String id = object.get("id").getAsString();
                String title = object.has("title") ? object.get("title").getAsString() : id;
                packs.add(new PresetPack(id, title));
            }
            return new PackPreset(file, name, saved, List.copyOf(packs));
        } catch (RuntimeException e) {
            throw new IOException("Invalid preset file", e);
        }
    }

    // ---------- write ----------

    private static void write(Path file, String name, long saved, List<PresetPack> packs) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("name", name);
        root.addProperty("saved", saved);

        JsonArray array = new JsonArray();
        for (PresetPack pack : packs) {
            JsonObject object = new JsonObject();
            object.addProperty("id", pack.id());
            object.addProperty("title", pack.title());
            array.add(object);
        }
        root.add("packs", array);

        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static String clean(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned.isEmpty() ? "preset" : cleaned;
    }

    private static Target resolve(String name, @Nullable Path current) throws IOException {
        String base = name.trim();
        if (base.isEmpty()) throw new IOException("Empty name");

        for (int index = 1; ; index++) {
            String candidate = index == 1 ? base : base + " " + index;
            Path file = directory().resolve(clean(candidate) + ".json");
            if (!Files.exists(file) || file.equals(current)) {
                return new Target(candidate, file);
            }
        }
    }

    public static PackPreset create(String name, List<PresetPack> packs) throws IOException {
        Target target = resolve(name, null);
        long now = System.currentTimeMillis();
        write(target.file(), target.name(), now, packs);
        return new PackPreset(target.file(), target.name(), now, List.copyOf(packs));
    }

    public static PackPreset overwrite(PackPreset preset, List<PresetPack> packs) throws IOException {
        long now = System.currentTimeMillis();
        write(preset.file(), preset.name(), now, packs);
        return new PackPreset(preset.file(), preset.name(), now, List.copyOf(packs));
    }

    public static PackPreset rename(PackPreset preset, String newName) throws IOException {
        Target target = resolve(newName, preset.file());
        write(target.file(), target.name(), preset.savedMillis(), preset.packs());
        if (!target.file().equals(preset.file())) {
            Files.deleteIfExists(preset.file());
        }
        return new PackPreset(target.file(), target.name(), preset.savedMillis(), preset.packs());
    }

    public static void delete(PackPreset preset) throws IOException {
        Files.deleteIfExists(preset.file());
    }
}