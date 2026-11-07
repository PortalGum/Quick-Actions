package com.quickactions.compat;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** everything that differs between game versions (26.3 uses sdl, 26.1/26.2 use the old api) */
public interface PlatformApi {
    void openPath(Path path) throws Exception;

    void openUrl(String url) throws Exception;

    /** future completes on the game thread, empty = cancelled or failed */
    CompletableFuture<Optional<Path>> showFileDialog(boolean save, String title, String filterName,
                                                     String pattern, String location, String errorMessage);

    /** completes exceptionally if the platform can't put images on the clipboard */
    CompletableFuture<Void> copyImage(Path file);
}