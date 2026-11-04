package com.quickactions.actions.server;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.server.ServerListTransfer.Entry;
import com.quickactions.actions.server.ServerListTransfer.ImportMode;
import com.quickactions.actions.server.ServerListTransfer.ImportResult;
import com.quickactions.ui.ChoiceScreen;
import com.quickactions.ui.notify.Notification;
import com.quickactions.util.ClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public final class ServerListOperations {
    private ServerListOperations() {
    }

    // ---------- export ----------

    public static void exportList(ServerListTarget target) {
        try {
            Path file = ServerListTransfer.export(target.servers());
            Path folder = file.getParent();
            Notification.success(Component.translatable("quickactions.notify.export.title"))
                    .message(Component.translatable("quickactions.notify.export.message", target.servers().size()))
                    .detail(file.toAbsolutePath().toString())
                    .action(Component.translatable("quickactions.notify.open_folder"), () -> ClientUtil.openPath(folder))
                    .duration(9000)
                    .show();
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to export server list", e);
            showFailure();
        }
    }

    // ---------- import ----------

    public static void openImport(ServerListTarget target) {
        Minecraft.getInstance().setScreenAndShow(new ImportServerListScreen(target.screen(), target));
    }

    public static void performImport(ServerListTarget target, List<Entry> entries, ImportMode mode) {
        try {
            Path backup = null;
            if (mode == ImportMode.REPLACE && target.servers().size() > 0) {
                backup = ServerListTransfer.backupCurrent(target.servers());
            }

            ImportResult result = ServerListTransfer.apply(target.servers(), entries, mode);

            if (backup != null) {
                Path backupFolder = backup.getParent();
                Notification.success(Component.translatable("quickactions.notify.import.title"))
                        .message(Component.translatable("quickactions.notify.import.replaced", result.added()))
                        .detail(backup.toAbsolutePath().toString())
                        .action(Component.translatable("quickactions.notify.open_folder"),
                                () -> ClientUtil.openPath(backupFolder))
                        .duration(9000)
                        .show();
            } else {
                Component message = result.skipped() == 0
                        ? Component.translatable("quickactions.notify.import.added", result.added())
                        : Component.translatable("quickactions.notify.import.added_skipped",
                        result.added(), result.skipped());
                Notification.success(Component.translatable("quickactions.notify.import.title"))
                        .message(message)
                        .show();
            }
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to import server list", e);
            showFailure();
        }
    }

    // ---------- clear ----------

    public static void openClearConfirm(ServerListTarget target) {
        int count = target.servers().size();

        Minecraft.getInstance().setScreenAndShow(new ChoiceScreen(
                target.screen(),
                Component.translatable("quickactions.clear.title"),
                Component.translatable("quickactions.clear.message", count),
                List.of(
                        new ChoiceScreen.Choice(
                                Component.translatable("quickactions.clear.save_and_clear"),
                                () -> clearList(target, true)),
                        new ChoiceScreen.Choice(
                                Component.translatable("quickactions.clear.clear_only"),
                                () -> clearList(target, false)),
                        new ChoiceScreen.Choice(CommonComponents.GUI_CANCEL, () -> { }))));
    }

    private static void clearList(ServerListTarget target, boolean saveFirst) {
        int count = target.servers().size();

        Path exported = null;
        if (saveFirst) {
            try {
                exported = ServerListTransfer.export(target.servers());
            } catch (Exception e) {
                QuickActionsClient.LOGGER.error("Failed to export server list before clearing", e);
                Notification.error(Component.translatable("quickactions.notify.failed.title"))
                        .message(Component.translatable("quickactions.notify.clear.not_saved"))
                        .duration(8000)
                        .show();
                return;
            }
        }

        ServerListTransfer.clear(target.servers());
        target.updateList().run();

        if (exported != null) {
            Path folder = exported.getParent();
            Notification.success(Component.translatable("quickactions.notify.clear.title"))
                    .message(Component.translatable("quickactions.notify.clear.saved", count))
                    .detail(exported.toAbsolutePath().toString())
                    .action(Component.translatable("quickactions.notify.open_folder"), () -> ClientUtil.openPath(folder))
                    .duration(9000)
                    .show();
        } else {
            Notification.success(Component.translatable("quickactions.notify.clear.title"))
                    .message(Component.translatable("quickactions.notify.clear.removed", count))
                    .show();
        }
    }

    public static void showFailure() {
        Notification.error(Component.translatable("quickactions.notify.failed.title"))
                .message(Component.translatable("quickactions.notify.failed.message"))
                .duration(8000)
                .show();
    }
}