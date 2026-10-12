package com.quickactions.actions.screenshot;

import com.mojang.blaze3d.platform.NativeImage;
import com.quickactions.QuickActionsClient;
import com.quickactions.util.FileOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class ScreenshotTextures {

    public enum Mode {
        FILL, FIT
    }

    public record Loaded(Identifier id, int width, int height, long readyMillis) {
    }

    private enum State {
        LOADING, READY, FAILED
    }

    private static final class Slot {
        final String key;
        final String infoKey;
        final Mode mode;
        volatile State state = State.LOADING;
        volatile boolean dead;
        volatile long wantedNanos;
        @Nullable Identifier id;
        int width;
        int height;
        long readyMillis;

        Slot(String key, String infoKey, Mode mode) {
            this.key = key;
            this.infoKey = infoKey;
            this.mode = mode;
        }

        Loaded loaded() {
            return new Loaded(id, width, height, readyMillis);
        }
    }

    private static final int MAX_FILL = 240;
    private static final int MAX_FIT = 6;
    private static final int MAX_SIDE = 4096;
    private static final long STALE_NANOS = 400_000_000L;

    private static final Map<String, Slot> FILL_SLOTS = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<String, Slot> FIT_SLOTS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, Loaded> FALLBACK = new HashMap<>();
    private static final AtomicLong COUNTER = new AtomicLong();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "QuickActions-Screenshots");
        thread.setDaemon(true);
        return thread;
    });

    static {
        ImageIO.setUseCache(false); // no temp files on disk
    }

    private ScreenshotTextures() {
    }

    // ---------- request ----------

    public static @Nullable Loaded get(ScreenshotInfo info, Mode mode, int pxW, int pxH) {
        int w = Math.max(1, Math.min(pxW, MAX_SIDE));
        int h = Math.max(1, Math.min(pxH, MAX_SIDE));
        Map<String, Slot> slots = slotsOf(mode);
        String key = info.key() + '|' + w + 'x' + h;

        Slot slot = slots.get(key);
        long now = System.nanoTime();
        if (slot == null) {
            slot = new Slot(key, info.key(), mode);
            slot.wantedNanos = now;
            slots.put(key, slot);
            submit(slot, info, w, h);
            evict(slots, mode == Mode.FILL ? MAX_FILL : MAX_FIT);
            return null;
        }
        slot.wantedNanos = now;
        return slot.state == State.READY ? slot.loaded() : null;
    }

    public static boolean failed(ScreenshotInfo info, Mode mode, int pxW, int pxH) {
        int w = Math.max(1, Math.min(pxW, MAX_SIDE));
        int h = Math.max(1, Math.min(pxH, MAX_SIDE));
        Slot slot = slotsOf(mode).get(info.key() + '|' + w + 'x' + h);
        return slot != null && slot.state == State.FAILED;
    }

    public static @Nullable Loaded fallback(ScreenshotInfo info) {
        return FALLBACK.get(info.key());
    }

    // ---------- free ----------

    public static void forget(ScreenshotInfo info) {
        String infoKey = info.key();
        for (Map<String, Slot> slots : java.util.List.of(FILL_SLOTS, FIT_SLOTS)) {
            Iterator<Slot> it = slots.values().iterator();
            while (it.hasNext()) {
                Slot slot = it.next();
                if (slot.infoKey.equals(infoKey)) {
                    it.remove();
                    release(slot);
                }
            }
        }
        FALLBACK.remove(infoKey);
    }

    public static void clear() {
        for (Map<String, Slot> slots : java.util.List.of(FILL_SLOTS, FIT_SLOTS)) {
            for (Slot slot : slots.values()) {
                release(slot);
            }
            slots.clear();
        }
        FALLBACK.clear();
    }

    private static Map<String, Slot> slotsOf(Mode mode) {
        return mode == Mode.FILL ? FILL_SLOTS : FIT_SLOTS;
    }

    private static void evict(Map<String, Slot> slots, int capacity) {
        Iterator<Slot> it = slots.values().iterator(); // oldest used to newest
        while (slots.size() > capacity && it.hasNext()) {
            Slot slot = it.next();
            if (slot.state != State.LOADING) {
                it.remove();
                release(slot);
            }
        }
    }

    private static void release(Slot slot) {
        slot.dead = true;
        Identifier id = slot.id;
        if (id == null) return;
        slot.id = null;
        Minecraft.getInstance().getTextureManager().release(id);
        FALLBACK.computeIfPresent(slot.infoKey, (key, value) -> value.id().equals(id) ? null : value);
    }

    // ---------- background work ----------

    private static void submit(Slot slot, ScreenshotInfo info, int pxW, int pxH) {
        POOL.execute(() -> {
            if (slot.dead) return;
            if (System.nanoTime() - slot.wantedNanos > STALE_NANOS) {
                Minecraft.getInstance().execute(() -> drop(slot));
                return;
            }
            byte[] data = null;
            try {
                data = render(info, slot.mode, pxW, pxH);
            } catch (Throwable error) {
                QuickActionsClient.LOGGER.warn("Failed to load screenshot {}", info.file(), error);
            }
            byte[] result = data;
            Minecraft.getInstance().execute(() -> finish(slot, result));
        });
    }

    private static void drop(Slot slot) {
        slot.dead = true;
        slotsOf(slot.mode).remove(slot.key, slot);
    }

    private static void finish(Slot slot, byte @Nullable [] data) {
        if (slot.dead) return;
        if (data == null) {
            slot.state = State.FAILED;
            return;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            int width = image.getWidth();
            int height = image.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath(
                    QuickActionsClient.MOD_ID, "screenshot/" + COUNTER.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(
                    id, new DynamicTexture(() -> "Quick Actions screenshot", image));

            slot.id = id;
            slot.width = width;
            slot.height = height;
            slot.readyMillis = System.currentTimeMillis();
            slot.state = State.READY;
            if (slot.mode == Mode.FILL) {
                FALLBACK.put(slot.infoKey, slot.loaded());
            }
        } catch (Throwable error) {
            QuickActionsClient.LOGGER.warn("Failed to create screenshot texture", error);
            slot.state = State.FAILED;
        }
    }

    private static byte @Nullable [] render(ScreenshotInfo info, Mode mode, int pxW, int pxH) throws Exception {
        if (mode == Mode.FIT) {
            // small png goes as is, no re-encode
            int[] size = FileOps.readPngSize(info.file());
            if (size != null && size[0] <= pxW && size[1] <= pxH) {
                return Files.readAllBytes(info.file());
            }
        }

        BufferedImage source = ImageIO.read(info.file().toFile());
        if (source == null) return null;

        BufferedImage result = mode == Mode.FILL ? cover(source, pxW, pxH) : fit(source, pxW, pxH);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        if (!ImageIO.write(result, "png", bytes)) return null;
        return bytes.toByteArray();
    }

    private static BufferedImage cover(BufferedImage source, int pxW, int pxH) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        double targetRatio = pxW / (double) pxH;
        int cw = sw;
        int ch = sh;
        if (sw / (double) sh > targetRatio) {
            cw = Math.max(1, (int) Math.round(sh * targetRatio));
        } else {
            ch = Math.max(1, (int) Math.round(sw / targetRatio));
        }
        BufferedImage cropped = source.getSubimage((sw - cw) / 2, (sh - ch) / 2, cw, ch);
        return scale(cropped, pxW, pxH);
    }

    private static BufferedImage fit(BufferedImage source, int pxW, int pxH) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        double factor = Math.min(1.0, Math.min(pxW / (double) sw, pxH / (double) sh));
        if (factor >= 1.0) return source;
        return scale(source, Math.max(1, (int) Math.round(sw * factor)), Math.max(1, (int) Math.round(sh * factor)));
    }

    private static BufferedImage scale(BufferedImage source, int targetW, int targetH) {
        BufferedImage current = source;
        int w = source.getWidth();
        int h = source.getHeight();
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2;
            h /= 2;
            current = draw(current, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (w != targetW || h != targetH) {
            current = draw(current, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return current;
    }

    private static BufferedImage draw(BufferedImage source, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
