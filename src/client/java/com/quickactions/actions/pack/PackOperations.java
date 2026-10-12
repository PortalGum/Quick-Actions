package com.quickactions.actions.pack;

import com.quickactions.QuickActionsClient;
import com.quickactions.ui.BusyRunner;
import com.quickactions.ui.ChoiceScreen;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import com.quickactions.util.ClientUtil;
import com.quickactions.util.FileOps;
import com.quickactions.util.FilePicker;
import com.quickactions.actions.pack.preset.PackSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.quickactions.actions.pack.DescriptionText.Flattened;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** all methods run on main thread on click */
public final class PackOperations {
    private static final int MAX_ICON_SIZE = 512;
    private static final String PICK_ICON_TITLE = "Select pack icon (square PNG)";

    private PackOperations() {
    }

    // ---------- no archive edits ----------

    public static void showInFolder(PackTarget target) {
        ClientUtil.showInFolder(target.path());
    }

    public static void copyPath(PackTarget target) {
        ClientUtil.copyToClipboard(
                target.path().toAbsolutePath().toString(),
                Component.translatable("quickactions.toast.path_copied"));
    }

    public static void moveToTop(PackTarget target) {
        PackSetup.moveToTop(target.model());
    }

    public static void moveToBottom(PackTarget target) {
        PackSetup.moveToBottom(target.model());
    }

    public static void rename(PackTarget target) {
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                target.screen(),
                Component.translatable("quickactions.pack.rename.title"),
                PackFiles.baseName(target.path()),
                100,
                newName -> {
                    try {
                        PackFiles.rename(target.path(), newName);
                    } catch (Exception e) {
                        reportFailure(e);
                    }
                }));
    }

    public static void duplicate(PackTarget target) {
        BusyRunner.run(
                target.screen(),
                Component.translatable("quickactions.busy.pack.duplicate"),
                () -> PackFiles.duplicate(target.path()),
                copy -> Notification.success(Component.translatable("quickactions.notify.pack.duplicated.title"))
                        .message(Component.translatable("quickactions.notify.pack.duplicated.message",
                                copy.getFileName().toString()))
                        .show(),
                PackOperations::reportFailure);
    }

    public static void delete(PackTarget target) {
        Minecraft.getInstance().setScreenAndShow(new ChoiceScreen(
                target.screen(),
                Component.translatable("quickactions.pack.delete.title"),
                Component.translatable("quickactions.pack.delete.message", target.fileName()),
                List.of(
                        new ChoiceScreen.Choice(
                                Component.translatable("quickactions.pack.delete.confirm"),
                                () -> deletePermanently(target)),
                        new ChoiceScreen.Choice(CommonComponents.GUI_CANCEL, () -> { })),
                true));
    }

    private static void deletePermanently(PackTarget target) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                PackFiles.delete(target.path());
                mc.execute(() -> Notification.success(Component.translatable("quickactions.notify.pack.deleted.title"))
                        .message(Component.literal(target.fileName()))
                        .show());
            } catch (Exception e) {
                mc.execute(() -> reportFailure(e));
            }
        });
    }

    // ---------- description ----------

    public static void editDescription(PackTarget target) {
        Flattened flat = readDescription(target);
        if (flat == null) return;

        if (!flat.lossy()) {
            openDescriptionEditor(target, flat.editable());
            return;
        }

        Minecraft.getInstance().setScreenAndShow(new ChoiceScreen(
                target.screen(),
                Component.translatable("quickactions.pack.description.lossy.title"),
                Component.translatable("quickactions.pack.description.lossy.message"),
                List.of(
                        new ChoiceScreen.Choice(
                                Component.translatable("quickactions.pack.description.lossy.confirm"),
                                () -> openDescriptionEditor(target, flat.editable())),
                        new ChoiceScreen.Choice(CommonComponents.GUI_CANCEL, () -> { })),
                true));
    }

    private static @Nullable Flattened readDescription(PackTarget target) {
        try {
            return DescriptionText.flatten(PackMeta.readDescription(target.path()));
        } catch (Exception e) {
            reportFailure(e);
            return null;
        }
    }

    private static void openDescriptionEditor(PackTarget target, String editable) {
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                target.screen(),
                Component.translatable("quickactions.pack.description.title"),
                editable,
                1024,
                Component.translatable("quickactions.pack.description.hint"),
                true,
                typed -> BusyRunner.run(
                        target.screen(),
                        Component.translatable("quickactions.busy.pack.update"),
                        () -> {
                            PackMeta.writeDescription(target.path(), DescriptionText.toJson(typed));
                            return target.fileName();
                        },
                        name -> Notification.success(
                                        Component.translatable("quickactions.notify.pack.description.title"))
                                .message(Component.literal(name))
                                .show(),
                        PackOperations::reportFailure)));
    }

    // ---------- icon ----------

    public static void changeIcon(PackTarget target) {
        Minecraft mc = Minecraft.getInstance();
        FilePicker.pickPng(PICK_ICON_TITLE).thenAccept(picked ->
                mc.execute(() -> picked.ifPresent(file -> applyIcon(target, file))));
    }

    private static void applyIcon(PackTarget target, Path file) {
        Component errorTitle = Component.translatable("quickactions.error.title");

        int[] size = FileOps.readPngSize(file);
        if (size == null) {
            ClientUtil.showAlert(target.screen(), errorTitle,
                    Component.translatable("quickactions.error.icon_not_png"));
            return;
        }
        if (size[0] != size[1]) {
            ClientUtil.showAlert(target.screen(), errorTitle,
                    Component.translatable("quickactions.error.pack_icon_not_square", size[0], size[1]));
            return;
        }
        if (size[0] > MAX_ICON_SIZE) {
            ClientUtil.showAlert(target.screen(), errorTitle,
                    Component.translatable("quickactions.error.pack_icon_too_big", size[0], size[1]));
            return;
        }

        BusyRunner.run(
                target.screen(),
                Component.translatable("quickactions.busy.pack.update"),
                () -> {
                    PackArchive.write(target.path(), PackArchive.ICON, Files.readAllBytes(file));
                    return target.fileName();
                },
                name -> Notification.success(Component.translatable("quickactions.notify.pack.icon_set.title"))
                        .message(Component.literal(name))
                        .show(),
                PackOperations::reportFailure);
    }

    public static void resetIcon(PackTarget target) {
        BusyRunner.run(
                target.screen(),
                Component.translatable("quickactions.busy.pack.update"),
                () -> {
                    PackArchive.remove(target.path(), PackArchive.ICON);
                    return target.fileName();
                },
                name -> Notification.success(Component.translatable("quickactions.notify.pack.icon_reset.title"))
                        .message(Component.literal(name))
                        .show(),
                PackOperations::reportFailure);
    }

    // ---------- compat ----------

    public static void fixCompatibility(PackTarget target) {
        BusyRunner.run(
                target.screen(),
                Component.translatable("quickactions.busy.pack.update"),
                () -> PackMeta.fixCompatibility(target.path()),
                result -> {
                    if (result.changed()) {
                        Notification.success(Component.translatable("quickactions.notify.pack.fixed.title"))
                                .message(Component.translatable("quickactions.notify.pack.fixed.message",
                                        result.minFormat(), result.maxFormat()))
                                .show();
                    } else {
                        Notification.info(Component.translatable("quickactions.notify.pack.nothing.title"))
                                .message(Component.translatable("quickactions.notify.pack.nothing.message"))
                                .show();
                    }
                },
                PackOperations::reportFailure);
    }

    // ---------- errors ----------

    /** friendly error instead of silence */
    private static void reportFailure(Throwable error) {
        QuickActionsClient.LOGGER.error("Pack operation failed", error);

        if (error instanceof PackMeta.MissingMetaException) {
            Notification.error(Component.translatable("quickactions.notify.pack.no_meta.title"))
                    .message(Component.translatable("quickactions.notify.pack.no_meta.message"))
                    .duration(8000)
                    .show();
        } else if (error instanceof FileSystemException && !(error instanceof NoSuchFileException)) {
            Notification.error(Component.translatable("quickactions.notify.pack.in_use.title"))
                    .message(Component.translatable("quickactions.notify.pack.in_use.message"))
                    .duration(8000)
                    .show();
        } else {
            Notifications.failure();
        }
    }
}