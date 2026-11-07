package com.quickactions.util;

import com.quickactions.compat.Platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** native open/save dialogs, the actual work is in com.quickactions.compat (sdl on 26.3, tinyfd on 26.1/26.2) */
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
        return Platform.get().showFileDialog(save, title, filterName, pattern, location, errorMessage);
    }
}