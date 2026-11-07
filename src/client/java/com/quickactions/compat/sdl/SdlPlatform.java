package com.quickactions.compat.sdl;

import com.mojang.blaze3d.Blaze3D;
import com.quickactions.compat.PlatformApi;
import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLMisc;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_ClipboardCleanupCallback;
import org.lwjgl.sdl.SDL_ClipboardDataCallback;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** 26.3+: everything goes through the game's own sdl3 */
public final class SdlPlatform implements PlatformApi {
    private static final String BMP = "image/bmp";
    private static final String PNG = "image/png";

    private record Encoded(byte[] bmp, byte[] png) {
    }

    @Override
    public void openPath(Path path) {
        Blaze3D.openPath(path.toAbsolutePath());
    }

    @Override
    public void openUrl(String url) {
        if (!SDLMisc.SDL_OpenURL(url)) {
            throw new IllegalStateException("SDL_OpenURL failed");
        }
    }

    // ---------------------------------------------------------------- file dialog

    @Override
    public CompletableFuture<Optional<Path>> showFileDialog(boolean save, String title, String filterName,
                                                            String pattern, String location, String errorMessage) {
        CompletableFuture<Optional<Path>> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        // sdl wants dialogs started from the main thread
        mc.execute(() -> openDialog(mc, future, save, title, filterName, pattern, location, errorMessage));
        return future;
    }

    private static void openDialog(Minecraft mc, CompletableFuture<Optional<Path>> future, boolean save, String title,
                                   String filterName, String pattern, String location, String errorMessage) {
        ByteBuffer nameBuf = MemoryUtil.memUTF8(filterName);
        ByteBuffer patternBuf = MemoryUtil.memUTF8(pattern); // sdl patterns are plain extensions: "png", not "*.png"
        SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
        filters.name(nameBuf).pattern(patternBuf);

        int props = SDLProperties.SDL_CreateProperties();
        SDL_DialogFileCallback[] holder = new SDL_DialogFileCallback[1];

        Runnable release = () -> {
            if (holder[0] != null) holder[0].free();
            if (props != 0) SDLProperties.SDL_DestroyProperties(props);
            filters.free();
            MemoryUtil.memFree(nameBuf);
            MemoryUtil.memFree(patternBuf);
        };

        holder[0] = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
            // null list = error, empty list = cancelled, otherwise null-terminated array of utf-8 paths
            Optional<Path> result = Optional.empty();
            Throwable problem = null;
            String sdlError = null;
            if (fileList == 0L) {
                sdlError = SDLError.SDL_GetError(); // thread-local, so read it right here in the callback
            } else {
                try {
                    long first = MemoryUtil.memGetAddress(fileList);
                    if (first != 0L) result = Optional.of(Path.of(MemoryUtil.memUTF8(first)));
                } catch (Throwable t) {
                    problem = t;
                }
            }

            Optional<Path> finalResult = result;
            Throwable finalProblem = problem;
            boolean finalError = fileList == 0L || problem != null;
            String finalSdlError = sdlError;
            // schedule() always queues, so the callback never gets freed while it's still running
            mc.schedule(() -> {
                release.run();
                if (finalError) {
                    Notifications.failure(errorMessage + (finalSdlError == null || finalSdlError.isBlank()
                            ? "" : " (SDL: " + finalSdlError + ")"), finalProblem);
                }
                future.complete(finalResult);
            });
        });

        try {
            SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address());
            SDLProperties.SDL_SetNumberProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1L);
            SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, location);
            SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
            SDLProperties.SDL_SetBooleanProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_MANY_BOOLEAN, false);

            SDLDialog.SDL_ShowFileDialogWithProperties(
                    save ? SDLDialog.SDL_FILEDIALOG_SAVEFILE : SDLDialog.SDL_FILEDIALOG_OPENFILE,
                    holder[0], 0L, props);
        } catch (Throwable t) {
            release.run();
            Notifications.failure(errorMessage, t);
            future.complete(Optional.empty());
        }
    }

    // ---------------------------------------------------------------- clipboard

    @Override
    public CompletableFuture<Void> copyImage(Path file) {
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
            png = Files.readAllBytes(file);
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

        provide[0] = SDL_ClipboardDataCallback.create((userdata, mimeType, size) -> {
            String mime = MemoryUtil.memUTF8Safe(mimeType);
            ByteBuffer buffer = BMP.equals(mime) ? bmp : PNG.equals(mime) ? png : null;
            MemoryUtil.memPutAddress(size, buffer == null ? 0L : buffer.remaining());
            return buffer == null ? 0L : MemoryUtil.memAddress(buffer);
        });

        cleanup[0] = SDL_ClipboardCleanupCallback.create(userdata -> mc.schedule(release));

        boolean ok;
        String sdlError = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer mimeTypes = stack.mallocPointer(2);
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