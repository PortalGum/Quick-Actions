package com.quickactions.actions.screenshot;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ScreenshotLibrary {
    /** gallery order: newest first */
    public static final Comparator<ScreenshotInfo> NEWEST_FIRST =
            Comparator.comparing(ScreenshotInfo::time).thenComparing(ScreenshotInfo::name).reversed();

    private ScreenshotLibrary() {
    }

    /** read one file (after rename for example) */
    public static ScreenshotInfo read(Path file) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        return ScreenshotInfo.of(file, attrs.lastModifiedTime().toMillis(), attrs.size());
    }

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("screenshots");
    }

    public static List<ScreenshotInfo> scan() throws IOException {
        Path dir = directory();
        if (!Files.isDirectory(dir)) return List.of();

        List<ScreenshotInfo> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!isImage(file)) continue;
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (!attrs.isRegularFile()) continue;
                    result.add(ScreenshotInfo.of(file, attrs.lastModifiedTime().toMillis(), attrs.size()));
                } catch (IOException ignored) {
                    // file gone or busy: skip
                }
            }
        }
        result.sort(NEWEST_FIRST);
        return result;
    }

    private static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}
