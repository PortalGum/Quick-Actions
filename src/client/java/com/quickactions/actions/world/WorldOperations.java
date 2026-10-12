package com.quickactions.actions.world;

import com.quickactions.ui.TextInputScreen;
import com.quickactions.QuickActionsClient;
import com.quickactions.util.ClientUtil;
import com.quickactions.util.FileOps;
import com.quickactions.util.FilePicker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** all methods run on main thread on click */
public final class WorldOperations {
    private static final Component ERROR_TITLE = Component.translatable("quickactions.error.title");

    private WorldOperations() {
    }

    // ---------- Duplicate ----------

    public static void duplicate(WorldTarget target) {
        Minecraft mc = Minecraft.getInstance();
        Path source = target.folder();
        Path savesDir = source.getParent();

        CompletableFuture.runAsync(() -> {
            Path newFolder = FileOps.findFreeDirectory(savesDir, target.levelId() + " (copy)");
            try {
                FileOps.copyDirectory(source, newFolder, Set.of("session.lock", "uid.dat"));

                try (LevelStorageSource.LevelStorageAccess access =
                             mc.getLevelSource().createAccess(newFolder.getFileName().toString())) {
                    access.renameLevel(target.displayName() + " (copy)");
                }
                mc.execute(target.refresh());
            } catch (Exception e) {
                QuickActionsClient.LOGGER.error("Failed to duplicate world {}", target.levelId(), e);
                try {
                    FileOps.deleteRecursively(newFolder); // don't leave a half-copied world
                } catch (Exception cleanup) {
                    QuickActionsClient.LOGGER.error("Failed to clean up {}", newFolder, cleanup);
                }
                mc.execute(() -> ClientUtil.showAlert(target.screen(), ERROR_TITLE,
                        Component.translatable("quickactions.error.duplicate_failed")));
            }
        });
    }

    // ---------- Rename ----------

    // ---------- Rename ----------

    public static void rename(WorldTarget target) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new TextInputScreen(
                target.screen(),
                Component.translatable("quickactions.rename.title"),
                target.displayName(),
                128,
                newName -> {
                    try (LevelStorageSource.LevelStorageAccess access =
                                 mc.getLevelSource().createAccess(target.levelId())) {
                        access.renameLevel(newName);
                    } catch (Exception e) {
                        QuickActionsClient.LOGGER.error("Failed to rename world {}", target.levelId(), e);
                        ClientUtil.showAlert(target.screen(), ERROR_TITLE,
                                Component.translatable("quickactions.error.rename_failed"));
                        return;
                    }
                    target.refresh().run(); // reread list to show new name right away
                }).onCancel(target.refresh()));
    }

    // ---------- Icon ----------

    public static void changeIcon(WorldTarget target) {
        Minecraft mc = Minecraft.getInstance();
        FilePicker.pickPng().thenAccept(picked ->
                mc.execute(() -> picked.ifPresent(file -> applyIcon(target, file))));
    }

    private static void applyIcon(WorldTarget target, Path file) {
        int[] size = com.quickactions.util.FileOps.readPngSize(file);
        if (size == null) {
            ClientUtil.showAlert(target.screen(), ERROR_TITLE,
                    Component.translatable("quickactions.error.icon_not_png"));
            return;
        }
        if (size[0] != 64 || size[1] != 64) {
            ClientUtil.showAlert(target.screen(), ERROR_TITLE,
                    Component.translatable("quickactions.error.icon_size", size[0], size[1]));
            return;
        }
        try {
            Files.copy(file, target.iconFile(), StandardCopyOption.REPLACE_EXISTING);
            target.refresh().run();
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to set icon for {}", target.levelId(), e);
            ClientUtil.showAlert(target.screen(), ERROR_TITLE,
                    Component.translatable("quickactions.error.icon_failed"));
        }
    }

    public static void resetIcon(WorldTarget target) {
        try {
            Files.deleteIfExists(target.iconFile());
            target.refresh().run();
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to reset icon for {}", target.levelId(), e);
            ClientUtil.showAlert(target.screen(), ERROR_TITLE,
                    Component.translatable("quickactions.error.icon_failed"));
        }
    }

    // ---------- Delete ----------

    public static void delete(WorldTarget target) {
        target.entry().deleteWorld();
    }
}