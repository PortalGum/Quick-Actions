package com.quickactions.actions.world.backup;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public record BackupInfo(Path file, String levelId, String worldName, long createdMillis, long sizeBytes) {
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern DEFAULT_NAME =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(_auto)?(_\\d+)?");

    private LocalDateTime time() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(createdMillis), ZoneId.systemDefault());
    }

    public String dateText() {
        return FULL.format(time());
    }

    public String shortDateText() {
        return SHORT.format(time());
    }

    /** file name without extension */
    public String fileBaseName() {
        String name = file.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    /** name given by player or null if default */
    public @Nullable String customName() {
        String base = fileBaseName();
        return DEFAULT_NAME.matcher(base).matches() ? null : base;
    }
}