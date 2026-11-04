package com.quickactions.actions.screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** file ops, no UI */
public final class ScreenshotFiles {
    private ScreenshotFiles() {
    }

    /** name without extension */
    public static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** extension with dot or empty string */
    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }

    public static Path rename(Path file, String newName) throws IOException {
        String extension = extension(file.getFileName().toString());

        String cleaned = newName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        if (!extension.isEmpty() && cleaned.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
            cleaned = cleaned.substring(0, cleaned.length() - extension.length()).trim();
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) throw new IOException("Empty name");

        Path dir = file.getParent();
        Path target = dir.resolve(cleaned + extension);
        int index = 2;
        while (Files.exists(target) && !Files.isSameFile(file, target)) {
            target = dir.resolve(cleaned + " " + index + extension);
            index++;
        }
        if (target.equals(file)) return file;

        Files.move(file, target);
        return target;
    }
}
