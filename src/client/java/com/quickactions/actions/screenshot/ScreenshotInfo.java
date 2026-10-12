package com.quickactions.actions.screenshot;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record ScreenshotInfo(Path file, String name, long sizeBytes, LocalDateTime time, boolean vanillaName) {
    private static final DateTimeFormatter VANILLA = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final int VANILLA_LENGTH = 19;

    public static ScreenshotInfo of(Path file, long modifiedMillis, long sizeBytes) {
        String name = file.getFileName().toString();
        LocalDateTime parsed = parseVanilla(name);
        LocalDateTime time = parsed != null
                ? parsed
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(modifiedMillis), ZoneId.systemDefault());
        return new ScreenshotInfo(file, name, sizeBytes, time, parsed != null);
    }

    private static @Nullable LocalDateTime parseVanilla(String name) {
        if (name.length() < VANILLA_LENGTH) return null;
        try {
            return LocalDateTime.parse(name.substring(0, VANILLA_LENGTH), VANILLA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public String key() {
        return file.toString() + '|' + sizeBytes;
    }
}
