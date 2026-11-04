package com.quickactions.actions.pack;

import com.quickactions.util.FileOps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class PackFiles {
    private static final String ZIP = ".zip";

    private PackFiles() {
    }

    private static boolean isZipName(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(ZIP);
    }

    /** name without .zip */
    public static String baseName(Path path) {
        String name = path.getFileName().toString();
        return isZipName(name) ? name.substring(0, name.length() - ZIP.length()) : name;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return isZipName(name) ? name.substring(name.length() - ZIP.length()) : "";
    }

    /** rename pack, keeps .zip */
    public static Path rename(Path pack, String newName) throws IOException {
        String extension = extension(pack);

        String cleaned = newName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        if (!extension.isEmpty() && isZipName(cleaned)) {
            cleaned = cleaned.substring(0, cleaned.length() - ZIP.length()).trim();
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) throw new IOException("Empty name");

        Path dir = pack.getParent();
        Path target = dir.resolve(cleaned + extension);
        int index = 2;
        while (Files.exists(target) && !Files.isSameFile(pack, target)) {
            target = dir.resolve(cleaned + " " + index + extension);
            index++;
        }
        if (target.equals(pack)) return pack;

        Files.move(pack, target);
        return target;
    }

    public static Path duplicate(Path pack) throws IOException {
        Path dir = pack.getParent();

        if (Files.isDirectory(pack)) {
            Path target = FileOps.findFreeDirectory(dir, pack.getFileName() + " (copy)");
            try {
                FileOps.copyDirectory(pack, target, Set.of());
            } catch (IOException | RuntimeException e) {
                try {
                    FileOps.deleteRecursively(target); // don't leave a half-copied folder
                } catch (IOException ignored) {
                    // not critical
                }
                throw e;
            }
            return target;
        }

        String base = baseName(pack) + " (copy)";
        String extension = extension(pack);
        Path target = dir.resolve(base + extension);
        int index = 2;
        while (Files.exists(target)) {
            target = dir.resolve(base + " " + index + extension);
            index++;
        }
        Files.copy(pack, target);
        return target;
    }

    /** delete pack for good */
    public static void delete(Path pack) throws IOException {
        if (Files.isDirectory(pack)) {
            FileOps.deleteRecursively(pack);
        } else {
            Files.delete(pack);
        }
    }
}