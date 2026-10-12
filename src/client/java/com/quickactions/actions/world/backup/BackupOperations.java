package com.quickactions.actions.world.backup;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.world.WorldListTarget;
import com.quickactions.actions.world.WorldTarget;
import com.quickactions.ui.BusyScreen;
import com.quickactions.ui.UiText;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import com.quickactions.util.ClientUtil;
import com.quickactions.util.FileOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** all methods run on main thread on click */
public final class BackupOperations {
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();

    private BackupOperations() {
    }

    // ---------- menu entry ----------

    public static void createBackup(WorldTarget target) {
        runBusy(target.screen(), target.refresh(), target.levelId(),
                Component.translatable("quickactions.busy.backup.title"),
                () -> BackupManager.createBackup(target.folder(), target.levelId(), target.displayName()),
                info -> {
                    Path folder = info.file().getParent();
                    Notification.success(Component.translatable("quickactions.notify.backup.created.title"))
                            .message(Component.translatable("quickactions.notify.backup.created.message",
                                    info.worldName(), UiText.formatBytes(info.sizeBytes())))
                            .detail(info.file().toAbsolutePath().toString())
                            .action(Component.translatable("quickactions.notify.open_folder"),
                                    () -> ClientUtil.openPath(folder))
                            .duration(9000)
                            .show();
                });
    }

    /** backups of one world */
    public static void openRestoreList(WorldTarget target) {
        Minecraft.getInstance().setScreenAndShow(
                new BackupListScreen(target.screen(), target.refresh(), target.levelId(), target.displayName()));
    }

    /** all backups grouped by world */
    public static void openAllBackups(WorldListTarget target) {
        Minecraft.getInstance().setScreenAndShow(
                new BackupListScreen(target.screen(), target.refresh(), null, null));
    }

    // ---------- actions from backup list ----------

    public static void rollBack(Screen returnTo, Runnable refresh, BackupInfo backup) {
        runBusy(returnTo, refresh, backup.levelId(),
                Component.translatable("quickactions.busy.restore.title"),
                () -> {
                    BackupManager.rollBack(backup);
                    return backup;
                },
                done -> Notification.success(Component.translatable("quickactions.notify.rollback.title"))
                        .message(Component.translatable("quickactions.notify.rollback.message",
                                done.worldName(), done.shortDateText()))
                        .duration(7000)
                        .show());
    }

    public static void restoreAsNew(Screen returnTo, Runnable refresh, BackupInfo backup) {
        Minecraft mc = Minecraft.getInstance();
        String newName = backup.worldName()
                + Component.translatable("quickactions.backup.restored_suffix", backup.shortDateText()).getString();

        runBusy(returnTo, refresh, backup.levelId(),
                Component.translatable("quickactions.busy.restore.title"),
                () -> {
                    Path folder = BackupManager.restoreAsNew(backup);
                    try (LevelStorageSource.LevelStorageAccess access =
                                 mc.getLevelSource().createAccess(folder.getFileName().toString())) {
                        access.renameLevel(newName);
                    } catch (Exception e) {
                        FileOps.deleteRecursively(folder); // don't leave world with wrong name
                        throw e;
                    }
                    return newName;
                },
                name -> Notification.success(Component.translatable("quickactions.notify.restore_new.title"))
                        .message(Component.translatable("quickactions.notify.restore_new.message", name))
                        .duration(8000)
                        .show());
    }

    // ---------- shared runner with busy screen ----------

    private static <R> void runBusy(Screen returnTo, Runnable refresh, String levelId, Component title,
                                    Callable<R> work, Consumer<R> onSuccess) {
        Minecraft mc = Minecraft.getInstance();

        if (!BUSY.add(levelId)) {
            mc.setScreenAndShow(returnTo);
            refresh.run();
            Notification.info(Component.translatable("quickactions.notify.busy")).duration(3500).show();
            return;
        }

        mc.setScreenAndShow(new BusyScreen(title, Component.translatable("quickactions.busy.message")));

        CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((result, error) -> mc.execute(() -> {
            BUSY.remove(levelId);
            mc.setScreenAndShow(returnTo);
            refresh.run();
            if (error != null) {
                QuickActionsClient.LOGGER.error("Backup operation failed", error);
                Notifications.failure();
            } else {
                onSuccess.accept(result);
            }
        }));
    }
}