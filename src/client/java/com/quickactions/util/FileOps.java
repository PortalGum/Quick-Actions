package com.quickactions.util;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** file ops with no Minecraft dependency */
public final class FileOps {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private FileOps() {
    }

    public static Path findFreeDirectory(Path parent, String baseName) {
        Path candidate = parent.resolve(baseName);
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + " " + index);
            index++;
        }
        return candidate;
    }

    public static void copyDirectory(Path source, Path target, Set<String> skipFileNames) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!skipFileNames.contains(file.getFileName().toString())) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static int @Nullable [] readPngSize(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(24);
            if (header.length < 24) return null;
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (header[i] != PNG_SIGNATURE[i]) return null;
            }
            int width = ByteBuffer.wrap(header, 16, 4).getInt();
            int height = ByteBuffer.wrap(header, 20, 4).getInt();
            return new int[]{width, height};
        } catch (IOException e) {
            return null;
        }
    }
}