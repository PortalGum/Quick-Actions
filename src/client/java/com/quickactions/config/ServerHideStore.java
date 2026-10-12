package com.quickactions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.quickactions.QuickActionsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public final class ServerHideStore {

    public enum HideField {
        MOTD(1), ONLINE(2), ICON(4);

        private final int mask;

        HideField(int mask) {
            this.mask = mask;
        }

        public int mask() {
            return mask;
        }
    }

    private static final HideField[] FIELDS = HideField.values();

    private static final class Flags {
        boolean motd;
        boolean online;
        boolean icon;

        boolean get(HideField field) {
            return switch (field) {
                case MOTD -> motd;
                case ONLINE -> online;
                case ICON -> icon;
            };
        }

        void set(HideField field, boolean value) {
            switch (field) {
                case MOTD -> motd = value;
                case ONLINE -> online = value;
                case ICON -> icon = value;
            }
        }
    }

    private static final class Overrides {
        Boolean motd;
        Boolean online;
        Boolean icon;

        Boolean get(HideField field) {
            return switch (field) {
                case MOTD -> motd;
                case ONLINE -> online;
                case ICON -> icon;
            };
        }

        void set(HideField field, Boolean value) {
            switch (field) {
                case MOTD -> motd = value;
                case ONLINE -> online = value;
                case ICON -> icon = value;
            }
        }

        boolean isEmpty() {
            return motd == null && online == null && icon == null;
        }
    }

    private static final class Data {
        Flags global = new Flags();
        Map<String, Overrides> servers = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("quickactions_hidden_servers.json");

    private static Data data = new Data();

    private ServerHideStore() {
    }

    private static String key(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) {
                if (loaded.global == null) loaded.global = new Flags();
                if (loaded.servers == null) loaded.servers = new HashMap<>();
                data = loaded;
                if (normalize()) {
                    save();
                }
            }
        } catch (IOException | JsonParseException e) {
            QuickActionsClient.LOGGER.error("Failed to read hidden servers file", e);
        }
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to save hidden servers file", e);
        }
    }

    private static boolean normalize() {
        boolean changed = false;
        Iterator<Map.Entry<String, Overrides>> iterator = data.servers.entrySet().iterator();
        while (iterator.hasNext()) {
            Overrides overrides = iterator.next().getValue();
            if (overrides == null) {
                iterator.remove();
                changed = true;
                continue;
            }
            for (HideField field : FIELDS) {
                Boolean own = overrides.get(field);
                if (own != null && own.booleanValue() == data.global.get(field)) {
                    overrides.set(field, null);
                    changed = true;
                }
            }
            if (overrides.isEmpty()) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    // ---------- final server state ----------

    public static int hiddenMask(String address) {
        Overrides overrides = data.servers.get(key(address));
        int mask = 0;
        for (HideField field : FIELDS) {
            Boolean own = overrides == null ? null : overrides.get(field);
            boolean hidden = own != null ? own : data.global.get(field);
            if (hidden) {
                mask |= field.mask();
            }
        }
        return mask;
    }

    public static boolean isHidden(String address, HideField field) {
        return (hiddenMask(address) & field.mask()) != 0;
    }

    public static void toggle(String address, HideField field) {
        boolean hide = !isHidden(address, field);
        String key = key(address);
        Overrides overrides = data.servers.get(key);

        if (hide == data.global.get(field)) {
            if (overrides != null) {
                overrides.set(field, null);
                if (overrides.isEmpty()) {
                    data.servers.remove(key);
                }
            }
        } else {
            data.servers.computeIfAbsent(key, k -> new Overrides()).set(field, hide);
        }
        save();
    }

    // ---------- global settings ----------

    public static boolean isGlobal(HideField field) {
        return data.global.get(field);
    }

    public static void toggleGlobal(HideField field) {
        data.global.set(field, !data.global.get(field));
        normalize();
        save();
    }

    public static void move(String oldAddress, String newAddress) {
        Overrides overrides = data.servers.remove(key(oldAddress));
        if (overrides != null) {
            data.servers.put(key(newAddress), overrides);
            save();
        }
    }
}