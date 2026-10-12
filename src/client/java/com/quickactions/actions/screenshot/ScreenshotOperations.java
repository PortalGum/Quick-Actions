package com.quickactions.actions.screenshot;

import com.quickactions.QuickActionsClient;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import com.quickactions.util.ClientUtil;
import com.quickactions.util.FilePicker;
import com.quickactions.util.SystemClipboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** all methods run on main thread on click */
public final class ScreenshotOperations {
    private ScreenshotOperations() {
    }

    public static void showInFolder(ScreenshotTarget target) {
        ClientUtil.showInFolder(target.info().file());
    }

    public static void copyPath(ScreenshotTarget target) {
        String path = target.info().file().toAbsolutePath().toString();
        ClientUtil.copyToClipboard(path, Component.translatable("quickactions.toast.path_copied"));
        Notification.success(Component.translatable("quickactions.toast.path_copied"))
                .detail(path)
                .duration(3500)
                .show();
    }

    // ---------- copy image ----------

    public static void copyImage(ScreenshotTarget target) {
        Minecraft mc = Minecraft.getInstance();
        SystemClipboard.copyImage(target.info().file()).whenComplete((ok, error) -> mc.execute(() -> {
            if (error != null || !Boolean.TRUE.equals(ok)) {
                if (error != null) QuickActionsClient.LOGGER.error("Clipboard copy failed", error);
                Notifications.failure();
                return;
            }
            Notification.success(Component.translatable("quickactions.notify.screenshot.copied.title"))
                    .message(Component.translatable("quickactions.notify.screenshot.copied.message"))
                    .duration(4000)
                    .show();
        }));
    }

    // ---------- save as ----------

    public static void saveAs(ScreenshotTarget target) {
        Path source = target.info().file();
        String name = source.getFileName().toString();
        String extension = ScreenshotFiles.extension(name);
        String ext = extension.isEmpty() ? "png" : extension.substring(1);

        Minecraft mc = Minecraft.getInstance();
        FilePicker.pickSavePath("Save screenshot as", name, ext).thenAccept(choice ->
                mc.execute(() -> choice.ifPresent(destination -> copyTo(source, destination))));
    }

    private static void copyTo(Path source, Path destination) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (Files.exists(destination) && Files.isSameFile(source, destination)) {
                Notification.info(Component.translatable("quickactions.notify.screenshot.same_file.title"))
                        .message(Component.translatable("quickactions.notify.screenshot.same_file.message"))
                        .duration(4000)
                        .show();
                return;
            }
        } catch (IOException ignored) {
            // compare failed, copy as usual
        }

        CompletableFuture.runAsync(() -> {
            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                mc.execute(() -> Notification.success(Component.translatable("quickactions.notify.screenshot.saved.title"))
                        .detail(destination.toAbsolutePath().toString())
                        .action(Component.translatable("quickactions.shots.button.show_in_folder"),
                                () -> ClientUtil.showInFolder(destination))
                        .duration(6000)
                        .show());
            } catch (IOException e) {
                QuickActionsClient.LOGGER.error("Failed to save screenshot to {}", destination, e);
                mc.execute(Notifications::failure);
            }
        });
    }

    // ---------- rename ----------

    public static void rename(ScreenshotTarget target) {
        Path file = target.info().file();
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                target.screen(),
                Component.translatable("quickactions.shots.rename.title"),
                ScreenshotFiles.baseName(target.info().name()),
                100,
                newName -> {
                    try {
                        Path renamed = ScreenshotFiles.rename(file, newName);
                        if (renamed.equals(file)) return;
                        target.onRenamed().accept(renamed);
                        Notification.success(Component.translatable("quickactions.notify.screenshot.renamed.title"))
                                .message(Component.literal(renamed.getFileName().toString()))
                                .duration(4000)
                                .show();
                    } catch (Exception e) {
                        QuickActionsClient.LOGGER.error("Failed to rename screenshot {}", file, e);
                        Notifications.failure();
                    }
                }));
    }

    // ---------- delete ----------

    public static void delete(ScreenshotTarget target) {
        Minecraft.getInstance().setScreenAndShow(new ScreenshotDeleteScreen(
                target.screen(), target.info(), () -> deletePermanently(target)));
    }

    private static void deletePermanently(ScreenshotTarget target) {
        try {
            Files.deleteIfExists(target.info().file());
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to delete screenshot {}", target.info().file(), e);
            Notifications.failure();
            return;
        }
        target.onDeleted().run();
        Notification.success(Component.translatable("quickactions.notify.screenshot.deleted.title"))
                .message(Component.literal(target.info().name()))
                .duration(4000)
                .show();
    }

    public static void deleteMany(Screen parent, List<ScreenshotInfo> infos, Consumer<List<ScreenshotInfo>> onDeleted) {
        Minecraft.getInstance().setScreenAndShow(new ScreenshotBulkDeleteScreen(
                parent, infos, () -> deleteManyNow(infos, onDeleted)));
    }

    private record Outcome(List<ScreenshotInfo> deleted, int failed) {
    }

    private static void deleteManyNow(List<ScreenshotInfo> infos, Consumer<List<ScreenshotInfo>> onDeleted) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> {
            List<ScreenshotInfo> deleted = new ArrayList<>();
            int failed = 0;
            for (ScreenshotInfo info : infos) {
                try {
                    Files.deleteIfExists(info.file());
                    deleted.add(info);
                } catch (IOException e) {
                    QuickActionsClient.LOGGER.error("Failed to delete screenshot {}", info.file(), e);
                    failed++;
                }
            }
            return new Outcome(deleted, failed);
        }).thenAccept(outcome -> mc.execute(() -> {
            if (!outcome.deleted().isEmpty()) {
                onDeleted.accept(outcome.deleted());
                Notification.success(Component.translatable("quickactions.notify.screenshot.deleted_many.title"))
                        .message(Component.translatable("quickactions.notify.screenshot.deleted_many.message",
                                outcome.deleted().size()))
                        .duration(4000)
                        .show();
            }
            if (outcome.failed() > 0) {
                Notification.error(Component.translatable("quickactions.notify.screenshot.delete_failed.title"))
                        .message(Component.translatable("quickactions.notify.screenshot.delete_failed.message",
                                outcome.failed()))
                        .duration(8000)
                        .show();
            }
        }));
    }
}
