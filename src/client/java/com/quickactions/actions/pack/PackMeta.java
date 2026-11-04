package com.quickactions.actions.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.quickactions.QuickActionsClient;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class PackMeta {
    private static final String MCMETA = "pack.mcmeta";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** no pack.mcmeta in pack root */
    public static final class MissingMetaException extends IOException {
        public MissingMetaException() {
            super("pack.mcmeta not found in the pack root");
        }
    }

    public record FixResult(boolean changed, int minFormat, int maxFormat) {
    }

    private record Loaded(JsonObject root, JsonObject pack) {
    }

    private PackMeta() {
    }

    // ---------- read and write ----------

    private static Loaded load(Path pack) throws IOException {
        byte[] bytes;
        try {
            bytes = PackArchive.read(pack, MCMETA);
        } catch (NoSuchFileException e) {
            throw new MissingMetaException();
        }

        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Invalid pack.mcmeta", e);
        }

        JsonObject packObject;
        if (root.has("pack") && root.get("pack").isJsonObject()) {
            packObject = root.getAsJsonObject("pack");
        } else {
            packObject = new JsonObject();
            root.add("pack", packObject);
        }
        return new Loaded(root, packObject);
    }

    private static void save(Path pack, Loaded meta) throws IOException {
        PackArchive.write(pack, MCMETA, GSON.toJson(meta.root()).getBytes(StandardCharsets.UTF_8));
    }

    // ---------- description ----------

    public static @Nullable JsonElement readDescription(Path pack) throws IOException {
        return load(pack).pack().get("description");
    }

    /** write description */
    public static void writeDescription(Path pack, JsonElement description) throws IOException {
        Loaded meta = load(pack);
        meta.pack().add("description", description);
        save(pack, meta);
    }

    // ---------- compat ----------

    private static int major(JsonElement element, int fallback) {
        try {
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                return array.isEmpty() ? fallback : major(array.get(0), fallback);
            }
            return (int) Math.floor(element.getAsDouble());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int major(@Nullable JsonElement element) {
        return element == null ? -1 : major(element, -1);
    }

    private static int @Nullable [] supportedRange(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        try {
            if (element.isJsonPrimitive()) {
                int value = (int) Math.floor(element.getAsDouble());
                return new int[]{value, value};
            }
            if (element.isJsonArray() && element.getAsJsonArray().size() >= 2) {
                JsonArray array = element.getAsJsonArray();
                return new int[]{major(array.get(0), -1), major(array.get(1), -1)};
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("min_inclusive") && object.has("max_inclusive")) {
                    return new int[]{major(object.get("min_inclusive"), -1), major(object.get("max_inclusive"), -1)};
                }
            }
        } catch (RuntimeException ignored) {
            // unrecognized format: assume no range
        }
        return null;
    }

    private static final int LEGACY_LIMIT = 65;

    public static FixResult fixCompatibility(Path pack) throws IOException {
        GameFormat.Version current = GameFormat.currentResourcePackFormat();
        QuickActionsClient.LOGGER.info("Fix compatibility: game resource pack format is {}.{}",
                current.major(), current.minor());

        Loaded meta = load(pack);
        JsonObject p = meta.pack();

        int packFormat = major(p.get("pack_format"));
        int[] supported = supportedRange(p.get("supported_formats"));
        boolean hasMin = p.has("min_format");
        boolean hasMax = p.has("max_format");

        int declaredMin = hasMin ? major(p.get("min_format"), current.major())
                : supported != null ? supported[0]
                : packFormat >= 0 ? packFormat : current.major();
        int declaredMax = hasMax ? major(p.get("max_format"), current.major())
                : supported != null ? supported[1]
                : packFormat >= 0 ? packFormat : current.major();

        int newMin = Math.min(declaredMin, current.major());
        int newMax = Math.max(declaredMax, current.major());

        boolean changed = false;

        if (!hasMin || newMin != declaredMin) {
            p.add("min_format", new JsonPrimitive(newMin));
            changed = true;
        }
        if (!hasMax || newMax != declaredMax) {
            if (newMax == current.major() && current.minor() != 0) {
                JsonArray pair = new JsonArray();
                pair.add(current.major());
                pair.add(current.minor());
                p.add("max_format", pair);
            } else {
                p.add("max_format", new JsonPrimitive(newMax));
            }
            changed = true;
        }

        changed |= syncLegacyFields(p, newMin, newMax, packFormat, supported);

        if (!changed) {
            return new FixResult(false, declaredMin, declaredMax);
        }
        save(pack, meta);
        return new FixResult(true, newMin, newMax);
    }

    private static boolean syncLegacyFields(JsonObject p, int min, int max, int packFormat, int @Nullable [] supported) {
        boolean changed = false;

        if (min >= LEGACY_LIMIT) {
            if (p.has("supported_formats")) {
                p.remove("supported_formats");
                changed = true;
            }
            return changed;
        }

        if (supported == null || supported[0] != min || supported[1] != max) {
            JsonArray range = new JsonArray();
            range.add(min);
            range.add(max);
            p.add("supported_formats", range);
            changed = true;
        }
        if (packFormat < min || packFormat > max) {
            p.add("pack_format", new JsonPrimitive(min));
            changed = true;
        }
        return changed;
    }
}