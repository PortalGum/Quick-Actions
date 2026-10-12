package com.quickactions.actions.world.backup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quickactions.util.FileOps;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int COMPRESSION_LEVEL = 4;
    private static final int MAX_NAME_LENGTH = 200;

    private BackupManager() {
    }

    // ---------- paths ----------

    private static Path quickActionsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("quickactions");
    }

    public static Path rootDir() {
        return quickActionsDir().resolve("backups");
    }

    private static Path tmpDir() {
        return quickActionsDir().resolve("tmp");
    }

    public static Path worldDir(String levelId) {
        return rootDir().resolve(levelId);
    }

    public static Path savesDir() {
        return FabricLoader.getInstance().getGameDir().resolve("saves");
    }

    // ---------- create ----------

    public static BackupInfo createBackup(Path worldFolder, String levelId, String worldName)
            throws IOException {
        if (!Files.isDirectory(worldFolder)) throw new IOException("World folder not found");

        Path dir = worldDir(levelId);
        Files.createDirectories(dir);

        long now = System.currentTimeMillis();
        String base = LocalDateTime.now().format(FILE_STAMP);
        Path file = uniqueFile(dir, base, ".zip");
        Path part = dir.resolve(file.getFileName() + ".part"); // not ready yet, hidden from list

        String safeName = worldName.length() > MAX_NAME_LENGTH ? worldName.substring(0, MAX_NAME_LENGTH) : worldName;
        JsonObject meta = new JsonObject();
        meta.addProperty("id", levelId);
        meta.addProperty("name", safeName);
        meta.addProperty("created", now);
        String comment = GSON.toJson(meta);

        try {
            try (ZipOutputStream zip = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(part)), StandardCharsets.UTF_8)) {
                zip.setLevel(COMPRESSION_LEVEL);
                zip.setComment(comment);

                Files.walkFileTree(worldFolder, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        ZipEntry entry = new ZipEntry(entryName(levelId, worldFolder, dir) + "/");
                        entry.setLastModifiedTime(attrs.lastModifiedTime());
                        zip.putNextEntry(entry);
                        zip.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        if (path.getFileName().toString().equals("session.lock")) {
                            return FileVisitResult.CONTINUE;
                        }
                        ZipEntry entry = new ZipEntry(entryName(levelId, worldFolder, path));
                        entry.setLastModifiedTime(attrs.lastModifiedTime());
                        zip.putNextEntry(entry);
                        Files.copy(path, zip);
                        zip.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            try {
                Files.move(part, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, file);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // not critical
            }
            throw e;
        }

        return new BackupInfo(file, levelId, safeName, now, Files.size(file));
    }

    private static String entryName(String levelId, Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return relative.isEmpty() ? levelId : levelId + "/" + relative;
    }

    private static Path uniqueFile(Path dir, String base, String extension) {
        Path candidate = dir.resolve(base + extension);
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(base + "_" + index + extension);
            index++;
        }
        return candidate;
    }

    // ---------- list ----------

    private static boolean isBackupFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public static boolean hasBackups(String levelId) {
        Path dir = worldDir(levelId);
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(BackupManager::isBackupFile);
        } catch (IOException e) {
            return false;
        }
    }

    public static List<BackupInfo> listFor(String levelId) {
        return listIn(worldDir(levelId));
    }

    public static List<BackupInfo> listAll() {
        Path root = rootDir();
        if (!Files.isDirectory(root)) return List.of();

        List<BackupInfo> result = new ArrayList<>();
        try (Stream<Path> worlds = Files.list(root)) {
            for (Path worldDir : worlds.filter(Files::isDirectory).toList()) {
                result.addAll(listIn(worldDir));
            }
        } catch (IOException ignored) {
            // return what we managed to collect
        }
        return result;
    }

    private static List<BackupInfo> listIn(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(BackupManager::isBackupFile)
                    .map(BackupManager::describe)
                    .sorted(Comparator.comparingLong(BackupInfo::createdMillis).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static BackupInfo describe(Path zip) {
        String levelId = zip.getParent().getFileName().toString();
        String name = levelId;
        long created = 0;
        long size = 0;

        try {
            created = Files.getLastModifiedTime(zip).toMillis();
            size = Files.size(zip);
        } catch (IOException ignored) {
            // data is optional
        }

        String comment = readZipComment(zip);
        if (comment != null && comment.startsWith("{")) {
            try {
                JsonObject meta = JsonParser.parseString(comment).getAsJsonObject();
                if (meta.has("name") && meta.get("name").isJsonPrimitive()) name = meta.get("name").getAsString();
                if (meta.has("created") && meta.get("created").isJsonPrimitive()) created = meta.get("created").getAsLong();
            } catch (RuntimeException ignored) {
                // foreign or broken comment: defaults stay
            }
        }
        return new BackupInfo(zip, levelId, name, created, size);
    }

    private static @Nullable String readZipComment(Path file) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            int read = (int) Math.min(length, 65_535 + 22);
            byte[] buffer = new byte[read];
            raf.seek(length - read);
            raf.readFully(buffer);

            for (int i = read - 22; i >= 0; i--) {
                if (buffer[i] == 0x50 && buffer[i + 1] == 0x4B && buffer[i + 2] == 0x05 && buffer[i + 3] == 0x06) {
                    int commentLength = (buffer[i + 20] & 0xFF) | ((buffer[i + 21] & 0xFF) << 8);
                    if (i + 22 + commentLength == read) {
                        return new String(buffer, i + 22, commentLength, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (IOException ignored) {
            // no comment: metadata comes from the file
        }
        return null;
    }

    // ---------- restore ----------

    private static void extract(Path zipFile, Path targetDir, Set<String> skipFileNames) throws IOException {
        Files.createDirectories(targetDir);
        Path root = targetDir.toAbsolutePath().normalize();

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                int slash = name.indexOf('/');
                if (slash < 0) continue;

                String relative = name.substring(slash + 1);
                if (relative.isEmpty()) continue;

                Path out = root.resolve(relative).normalize();
                if (!out.startsWith(root)) throw new IOException("Unsafe entry in backup: " + name);

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (skipFileNames.contains(out.getFileName().toString())) continue;
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void rollBack(BackupInfo backup) throws IOException {
        String levelId = backup.levelId();
        Path world = savesDir().resolve(levelId);
        if (!Files.isDirectory(world)) throw new IOException("World folder not found");

        Files.createDirectories(tmpDir());
        long stamp = System.currentTimeMillis();
        Path restored = tmpDir().resolve(levelId + "-restore-" + stamp);
        Path old = tmpDir().resolve(levelId + "-old-" + stamp);

        try {
            extract(backup.file(), restored, Set.of());
            if (!Files.isRegularFile(restored.resolve("level.dat"))) {
                throw new IOException("Backup does not contain a world");
            }

            Files.move(world, old);
            try {
                Files.move(restored, world);
            } catch (IOException e) {
                Files.move(old, world); // put old world back
                throw e;
            }
        } catch (IOException | RuntimeException e) {
            try {
                FileOps.deleteRecursively(restored);
            } catch (IOException ignored) {
                // not critical
            }
            throw e;
        }

        try {
            FileOps.deleteRecursively(old);
        } catch (IOException ignored) {
        }
    }

    public static Path restoreAsNew(BackupInfo backup) throws IOException {
        Path folder = FileOps.findFreeDirectory(savesDir(), backup.levelId() + " (restored)");
        try {
            extract(backup.file(), folder, Set.of("uid.dat"));
            if (!Files.isRegularFile(folder.resolve("level.dat"))) {
                throw new IOException("Backup does not contain a world");
            }
        } catch (IOException | RuntimeException e) {
            try {
                FileOps.deleteRecursively(folder);
            } catch (IOException ignored) {
                // not critical
            }
            throw e;
        }
        return folder;
    }

    public static void delete(BackupInfo backup) throws IOException {
        Files.deleteIfExists(backup.file());
        try {
            Files.delete(backup.file().getParent()); // removed only if folder is empty
        } catch (IOException ignored) {
            // folder has other backups
        }
    }

    public static void deleteAll(String levelId) throws IOException {
        if (Files.isDirectory(savesDir().resolve(levelId))) {
            throw new IOException("World still exists");
        }

        Path dir = worldDir(levelId);
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.filter(BackupManager::isBackupOrLeftover).toList()) {
                Files.deleteIfExists(path);
            }
        }
        try {
            Files.delete(dir); // removed only if folder is empty
        } catch (IOException ignored) {
            // foreign files in folder, don't touch
        }
    }

    private static boolean isBackupOrLeftover(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.endsWith(".zip") || name.endsWith(".part"));
    }

    public static Path rename(BackupInfo backup, String newName) throws IOException {
        String cleaned = newName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        if (cleaned.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) throw new IOException("Empty name");

        Path file = backup.file();
        Path dir = file.getParent();
        Path target = dir.resolve(cleaned + ".zip");
        int index = 2;
        while (Files.exists(target) && !Files.isSameFile(file, target)) {
            target = dir.resolve(cleaned + " " + index + ".zip");
            index++;
        }
        if (target.equals(file)) return file;

        Files.move(file, target);
        return target;
    }
}