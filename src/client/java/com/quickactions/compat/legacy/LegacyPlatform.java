package com.quickactions.compat.legacy;

import com.quickactions.compat.PlatformApi;
import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 26.1 / 26.2: no sdl here, and this class is compiled against 26.3 where the old api is gone,
 * so everything old is called by name through reflection (nothing here may import sdl or tinyfd)
 */
public final class LegacyPlatform implements PlatformApi {

    // ---------------------------------------------------------------- open path / url

    @Override
    public void openPath(Path path) throws Exception {
        Path abs = path.toAbsolutePath();
        if (callUtilPlatform("openPath", abs)) return;
        if (callUtilPlatform("openFile", abs.toFile())) return;
        openWithSystemTool(abs.toString());
    }

    @Override
    public void openUrl(String url) throws Exception {
        if (callUtilPlatform("openUri", url)) return;
        if (callUtilPlatform("openUri", URI.create(url))) return;
        if (callUtilPlatform("openUrl", url)) return;
        openWithSystemTool(url);
    }

    /** Util.getPlatform().<name>(arg), false if this game version has no such method */
    private static boolean callUtilPlatform(String name, Object arg) throws Exception {
        Object platform;
        try {
            Class<?> util = Class.forName("net.minecraft.Util");
            platform = util.getMethod("getPlatform").invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
        // enum constants can be anonymous subclasses, so look at the enum class itself
        Class<?> type = platform instanceof Enum<?> e ? e.getDeclaringClass() : platform.getClass();
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isInstance(arg)) {
                m.invoke(platform, arg);
                return true;
            }
        }
        return false;
    }

    /** last resort if the game has no usable method */
    private static void openWithSystemTool(String target) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[]{"explorer.exe", target}
                : os.contains("mac") ? new String[]{"open", target}
                : new String[]{"xdg-open", target};
        new ProcessBuilder(cmd).start();
    }

    // ------ file dialog (tinyfd)

    @Override
    public CompletableFuture<Optional<Path>> showFileDialog(boolean save, String title, String filterName,
                                                            String pattern, String location, String errorMessage) {
        CompletableFuture<Optional<Path>> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        // tinyfd blocks until the user picks something, same as vanilla used to do it on the main thread
        mc.execute(() -> {
            try {
                String chosen = runTinyFd(save, title, filterName, pattern, location);
                future.complete(chosen == null || chosen.isBlank() ? Optional.empty() : Optional.of(Path.of(chosen)));
            } catch (Throwable t) {
                Notifications.failure(errorMessage, t);
                future.complete(Optional.empty());
            }
        });
        return future;
    }

    private static String runTinyFd(boolean save, String title, String filterName, String pattern, String location)
            throws Exception {
        Class<?> tfd = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*." + pattern)).flip(); // tinyfd wants "*.png"
            if (save) {
                Method m = tfd.getMethod("tinyfd_saveFileDialog",
                        CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class);
                return (String) m.invoke(null, title, location, filters, filterName);
            }
            Method m = tfd.getMethod("tinyfd_openFileDialog",
                    CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class, boolean.class);
            return (String) m.invoke(null, title, location, filters, filterName, false);
        }
    }

    // ----------- clipboard

    @Override
    public CompletableFuture<Void> copyImage(Path file) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            // nobody asked for linux/mac on old versions yet
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "Copying images to the clipboard works on Windows or Minecraft 26.3+"));
        }

        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> {
            try {
                return WindowsClipboard.encodeDib(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).whenComplete((dib, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            // clipboard calls stay on the game thread
            mc.execute(() -> {
                try {
                    WindowsClipboard.put(dib);
                    result.complete(null);
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
        });
        return result;
    }
}