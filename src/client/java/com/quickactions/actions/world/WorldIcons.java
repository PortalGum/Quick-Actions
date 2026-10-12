package com.quickactions.actions.world;

import com.mojang.blaze3d.platform.NativeImage;
import com.quickactions.QuickActionsClient;
import com.quickactions.actions.world.backup.BackupManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class WorldIcons {
    private static final long MAX_ICON_BYTES = 1024 * 1024;
    private static final String ICON_NAME = "icon.png";

    private enum State {
        LOADING, READY, NONE
    }

    private static final class Slot {
        volatile State state = State.LOADING;
        volatile boolean dead;
        @Nullable Identifier id;
    }

    private static final Map<String, Slot> SLOTS = new HashMap<>();
    private static final AtomicLong COUNTER = new AtomicLong();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "QuickActions-WorldIcons");
        thread.setDaemon(true);
        return thread;
    });

    private WorldIcons() {
    }

    public static @Nullable Identifier get(String levelId, List<Path> backups) {
        Slot slot = SLOTS.get(levelId);
        if (slot == null) {
            slot = new Slot();
            SLOTS.put(levelId, slot);
            submit(slot, levelId, List.copyOf(backups));
            return null;
        }
        return slot.state == State.READY ? slot.id : null;
    }

    public static boolean missing(String levelId) {
        Slot slot = SLOTS.get(levelId);
        return slot != null && slot.state == State.NONE;
    }

    public static void clear() {
        for (Slot slot : SLOTS.values()) {
            slot.dead = true;
            Identifier id = slot.id;
            if (id != null) {
                slot.id = null;
                Minecraft.getInstance().getTextureManager().release(id);
            }
        }
        SLOTS.clear();
    }

    // ---------- background work ----------

    private static void submit(Slot slot, String levelId, List<Path> backups) {
        POOL.execute(() -> {
            if (slot.dead) return;
            byte[] data = null;
            try {
                data = findIcon(levelId, backups);
            } catch (Throwable error) {
                QuickActionsClient.LOGGER.warn("Failed to read icon of world {}", levelId, error);
            }
            byte[] result = data;
            Minecraft.getInstance().execute(() -> finish(slot, result));
        });
    }

    private static byte @Nullable [] findIcon(String levelId, List<Path> backups) throws IOException {
        Path own = BackupManager.savesDir().resolve(levelId).resolve(ICON_NAME);
        if (Files.isRegularFile(own) && Files.size(own) <= MAX_ICON_BYTES) {
            return Files.readAllBytes(own);
        }
        for (Path backup : backups) {
            if (!Files.isRegularFile(backup)) continue;
            try (ZipFile zip = new ZipFile(backup.toFile(), StandardCharsets.UTF_8)) {
                ZipEntry entry = zip.getEntry(levelId + "/" + ICON_NAME);
                if (entry == null || entry.isDirectory() || entry.getSize() > MAX_ICON_BYTES) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    return in.readNBytes((int) MAX_ICON_BYTES);
                }
            } catch (IOException | RuntimeException e) {
                // broken archive: try next backup
            }
        }
        return null;
    }

    private static void finish(Slot slot, byte @Nullable [] data) {
        if (slot.dead) return;
        if (data == null || data.length == 0) {
            slot.state = State.NONE;
            return;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            Identifier id = Identifier.fromNamespaceAndPath(
                    QuickActionsClient.MOD_ID, "world_icon/" + COUNTER.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(
                    id, new DynamicTexture(() -> "Quick Actions world icon", image));
            slot.id = id;
            slot.state = State.READY;
        } catch (Throwable error) {
            QuickActionsClient.LOGGER.warn("Failed to create world icon texture", error);
            slot.state = State.NONE;
        }
    }
}
