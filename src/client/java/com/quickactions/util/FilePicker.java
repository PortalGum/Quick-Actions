package com.quickactions.util;

import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * native open/save dialogs through sdl3 (26.3 dropped lwjgl-tinyfd)
 *
 * sdl dialogs are async: call returns right away, the choice shows up in a callback
 * the future always completes on the game thread so touching game state is fine
 * everything we give to sdl (filters, strings, properties, callback) stays alive until the callback fires, freed after that
 */
public final class FilePicker {
    private static final String DEFAULT_TITLE = "Select world icon (64x64 PNG)";

    private FilePicker() {
    }

    public static CompletableFuture<Optional<Path>> pickPng() {
        return pickPng(DEFAULT_TITLE);
    }

    public static CompletableFuture<Optional<Path>> pickPng(String title) {
        String home = System.getProperty("user.home", "") + File.separator;
        return show(false, title, "PNG images", "png", home, "File picker failed");
    }

    public static CompletableFuture<Optional<Path>> pickSavePath(String title, String fileName, String extension) {
        String ext = extension.toLowerCase(Locale.ROOT);
        Path start = Path.of(System.getProperty("user.home", ""), "Pictures");
        if (!Files.isDirectory(start)) start = Path.of(System.getProperty("user.home", ""));
        String location = start.resolve(fileName).toString();

        return show(true, title, ext.toUpperCase(Locale.ROOT) + " image", ext, location, "Save dialog failed")
                .thenApply(chosen -> chosen.map(path -> {
                    String name = path.getFileName().toString();
                    return name.toLowerCase(Locale.ROOT).endsWith("." + ext)
                            ? path
                            : path.resolveSibling(name + "." + ext);
                }));
    }

    private static CompletableFuture<Optional<Path>> show(boolean save, String title, String filterName,
                                                          String pattern, String location, String errorMessage) {
        CompletableFuture<Optional<Path>> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        // sdl wants dialogs started from the main thread
        mc.execute(() -> open(mc, future, save, title, filterName, pattern, location, errorMessage));
        return future;
    }

    private static void open(Minecraft mc, CompletableFuture<Optional<Path>> future, boolean save, String title,
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
            // schedule() always queues (execute() would run inline on the game thread),
            // so the callback never gets freed while it's still running, even if sdl fires it synchronously
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
}