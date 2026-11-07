package com.quickactions.util;

import com.quickactions.compat.Platform;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * puts a screenshot on the system clipboard as an image
 * works on 26.3+ only (sdl), on older versions the future completes with UnsupportedOperationException,
 * so the caller's existing error toast shows up
 */
public final class SystemClipboard {
    private SystemClipboard() {
    }

    public static CompletableFuture<Void> copyImage(Path file) {
        return Platform.get().copyImage(file);
    }
}