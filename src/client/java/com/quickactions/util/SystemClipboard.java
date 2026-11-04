package com.quickactions.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_ClipboardCleanupCallback;
import org.lwjgl.sdl.SDL_ClipboardDataCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * puts a screenshot on the system clipboard as an image, through the game's own sdl
 * (no awt because the game runs headless, no external processes either)
 *
 * sdl serves the data lazily: image gets encoded once on a worker thread, then goes to sdl
 * on the game thread together with two callbacks that hand out the bytes when something pastes
 * native memory and callbacks stay alive until sdl says the clipboard got replaced
 *
 * windows wants image/bmp (becomes CF_DIB, every app understands it), png is offered too for other platforms
 * if it fails the future completes with an error message, so the caller can log it and show the toast
 */
public final class SystemClipboard {
    private static final String BMP = "image/bmp";
    private static final String PNG = "image/png";

    private record Encoded(byte[] bmp, byte[] png) {
    }

    private SystemClipboard() {
    }

    public static CompletableFuture<Void> copyImage(Path file) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<Void> result = new CompletableFuture<>();

        CompletableFuture.supplyAsync(() -> {
            try {
                return encode(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).whenComplete((encoded, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            // sdl wants this on the main thread
            mc.execute(() -> {
                try {
                    offer(mc, encoded);
                    result.complete(null);
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
        });
        return result;
    }

    private static Encoded encode(Path file) throws IOException {
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) {
            throw new IOException("Not a readable image (no ImageIO reader accepted it): " + file);
        }

        // bmp has no alpha and imageio refuses argb, so go through plain rgb
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, width, height, source.getRGB(0, 0, width, height, null, 0, width), 0, width);

        ByteArrayOutputStream bmp = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "bmp", bmp)) {
            throw new IOException("No ImageIO writer for BMP");
        }

        byte[] png;
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            png = Files.readAllBytes(file); // already png, no need to re-encode
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(source, "png", out)) {
                throw new IOException("No ImageIO writer for PNG");
            }
            png = out.toByteArray();
        }
        return new Encoded(bmp.toByteArray(), png);
    }

    private static void offer(Minecraft mc, Encoded data) {
        ByteBuffer bmp = copyToNative(data.bmp());
        ByteBuffer png = copyToNative(data.png());

        SDL_ClipboardDataCallback[] provide = new SDL_ClipboardDataCallback[1];
        SDL_ClipboardCleanupCallback[] cleanup = new SDL_ClipboardCleanupCallback[1];
        Runnable release = () -> {
            if (provide[0] != null) provide[0].free();
            if (cleanup[0] != null) cleanup[0].free();
            MemoryUtil.memFree(bmp);
            MemoryUtil.memFree(png);
        };

        // sdl calls this when some other app pastes
        provide[0] = SDL_ClipboardDataCallback.create((userdata, mimeType, size) -> {
            String mime = MemoryUtil.memUTF8Safe(mimeType);
            ByteBuffer buffer = BMP.equals(mime) ? bmp : PNG.equals(mime) ? png : null;
            MemoryUtil.memPutAddress(size, buffer == null ? 0L : buffer.remaining());
            return buffer == null ? 0L : MemoryUtil.memAddress(buffer);
        });

        // sdl calls this when the clipboard gets replaced or cleared, queued because
        // freeing a callback from inside its own call would pull the code out from under it
        cleanup[0] = SDL_ClipboardCleanupCallback.create(userdata -> mc.schedule(release));

        boolean ok;
        String sdlError = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer mimeTypes = stack.mallocPointer(2); // sdl copies the list
            mimeTypes.put(stack.UTF8(BMP)).put(stack.UTF8(PNG)).flip();
            ok = SDLClipboard.SDL_SetClipboardData(provide[0], cleanup[0], 0L, mimeTypes);
            if (!ok) sdlError = SDLError.SDL_GetError();
        } catch (Throwable t) {
            release.run();
            throw t;
        }

        if (!ok) {
            release.run();
            throw new IllegalStateException("SDL_SetClipboardData failed"
                    + (sdlError == null || sdlError.isBlank() ? "" : ": " + sdlError));
        }
    }

    private static ByteBuffer copyToNative(byte[] bytes) {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }
}