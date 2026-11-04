package com.quickactions.actions.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackArchive {
    public static final String ICON = "pack.png";

    private PackArchive() {
    }

    public static boolean hasEntry(Path pack, String name) {
        try {
            if (Files.isDirectory(pack)) {
                return Files.isRegularFile(pack.resolve(name));
            }
            try (ZipFile zip = new ZipFile(pack.toFile())) {
                return zip.getEntry(name) != null;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static byte[] read(Path pack, String name) throws IOException {
        if (Files.isDirectory(pack)) {
            return Files.readAllBytes(pack.resolve(name));
        }
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) throw new NoSuchFileException(name);
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Unreadable archive", e);
        }
    }

    /** replace file in pack root or add new */
    public static void write(Path pack, String name, byte[] data) throws IOException {
        if (Files.isDirectory(pack)) {
            Files.write(pack.resolve(name), data);
            return;
        }
        try (FileSystem fs = FileSystems.newFileSystem(pack)) {
            Files.write(fs.getPath("/", name), data);
        } catch (RuntimeException e) {
            throw new IOException("Cannot modify archive", e);
        }
    }

    public static void remove(Path pack, String name) throws IOException {
        if (Files.isDirectory(pack)) {
            Files.deleteIfExists(pack.resolve(name));
            return;
        }
        try (FileSystem fs = FileSystems.newFileSystem(pack)) {
            Files.deleteIfExists(fs.getPath("/", name));
        } catch (RuntimeException e) {
            throw new IOException("Cannot modify archive", e);
        }
    }
}