package com.quickactions.actions.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.quickactions.QuickActionsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class ServerListTransfer {
    public enum ImportMode {ADD, REPLACE}

    public record Entry(String name, String ip) {
    }

    public record ListFile(Path path, long modifiedMillis, int serverCount) {
        public String displayName() {
            String name = path.getFileName().toString();
            return name.toLowerCase(Locale.ROOT).endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        }
    }

    public record ImportResult(int added, int skipped, int removed) {
    }

    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ServerListTransfer() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("quickactions").resolve("server_lists");
    }

    public static Path backupDirectory() {
        return directory().resolve("backups");
    }

    // ---------- write ----------

    public static Path export(ServerList servers) throws IOException {
        return write(directory(), "servers_" + stamp(), snapshot(servers));
    }

    /** copy of current list before replace */
    public static Path backupCurrent(ServerList servers) throws IOException {
        return write(backupDirectory(), "before-import_" + stamp(), snapshot(servers));
    }

    private static String stamp() {
        return LocalDateTime.now().format(FILE_STAMP);
    }

    private static List<Entry> snapshot(ServerList servers) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) {
            ServerData data = servers.get(i);
            entries.add(new Entry(data.name, data.ip));
        }
        return entries;
    }

    private static Path write(Path dir, String baseName, List<Entry> entries) throws IOException {
        Files.createDirectories(dir);

        Path file = dir.resolve(baseName + ".json");
        int index = 2;
        while (Files.exists(file)) {
            file = dir.resolve(baseName + "_" + index + ".json");
            index++;
        }

        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("exported", LocalDateTime.now().toString());
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("name", entry.name());
            object.addProperty("ip", entry.ip());
            array.add(object);
        }
        root.add("servers", array);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        return file;
    }

    // ---------- read ----------

    public static List<Entry> read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IOException("Not a server list file");

            JsonElement array = root.getAsJsonObject().get("servers");
            if (array == null || !array.isJsonArray()) throw new IOException("No servers array");

            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : array.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String name = string(object, "name");
                String ip = string(object, "ip");
                if (ip == null || ip.isBlank()) continue;
                entries.add(new Entry(name == null || name.isBlank() ? ip.trim() : name, ip.trim()));
            }
            return entries;
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON", e);
        }
    }

    private static @Nullable String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /** files from lists folder, newest first */
    public static List<ListFile> listFiles() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) return List.of();

        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> Files.isRegularFile(p)
                            && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .map(ServerListTransfer::describe)
                    .sorted(Comparator.<ListFile>comparingLong(ListFile::modifiedMillis).reversed())
                    .limit(100)
                    .toList();
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to list server lists", e);
            return List.of();
        }
    }

    private static ListFile describe(Path path) {
        long modified = 0;
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            // date not critical
        }
        int count = -1;
        try {
            count = read(path).size();
        } catch (IOException ignored) {
            // file is marked invalid
        }
        return new ListFile(path, modified, count);
    }

    // ---------- apply ----------

    private static String normalize(String ip) {
        return ip.trim().toLowerCase(Locale.ROOT);
    }

    public static ImportResult apply(ServerList servers, List<Entry> entries, ImportMode mode) {
        int removed = 0;
        if (mode == ImportMode.REPLACE) {
            while (servers.size() > 0) {
                int before = servers.size();
                servers.remove(servers.get(0));
                if (servers.size() == before) break; // loop guard
                removed++;
            }
        }

        Set<String> known = new HashSet<>();
        for (int i = 0; i < servers.size(); i++) {
            known.add(normalize(servers.get(i).ip));
        }

        int added = 0;
        int skipped = 0;
        for (Entry entry : entries) {
            if (!known.add(normalize(entry.ip()))) {
                skipped++;
                continue;
            }
            servers.add(new ServerData(entry.name(), entry.ip(), ServerData.Type.OTHER), false);
            added++;
        }

        servers.save();
        return new ImportResult(added, skipped, removed);
    }

    // ---------- rename and delete ----------

    public static Path rename(Path file, String newName) throws IOException {
        String cleaned = newName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        if (cleaned.toLowerCase(Locale.ROOT).endsWith(".json")) {
            cleaned = cleaned.substring(0, cleaned.length() - 5).trim();
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) throw new IOException("Empty name");

        Path dir = file.getParent();
        Path target = dir.resolve(cleaned + ".json");
        int index = 2;
        while (Files.exists(target) && !Files.isSameFile(file, target)) {
            target = dir.resolve(cleaned + " " + index + ".json");
            index++;
        }
        if (target.equals(file)) return file;

        Files.move(file, target);
        return target;
    }

    public static void delete(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    public static int clear(ServerList servers) {
        int removed = 0;
        while (servers.size() > 0) {
            int before = servers.size();
            servers.remove(servers.get(0));
            if (servers.size() == before) break; // loop guard
            removed++;
        }
        servers.save();
        return removed;
    }
}