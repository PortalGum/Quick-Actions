package com.quickactions.actions.world;

import com.quickactions.actions.world.backup.BackupManager;
import com.quickactions.actions.world.backup.BackupOperations;
import com.quickactions.api.ActionCategory;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.QuickAction;
import com.quickactions.api.QuickActionGroup;
import com.quickactions.api.SimpleQuickAction;
import com.quickactions.util.ClientUtil;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class WorldActions implements ActionProvider<WorldTarget> {

    private final List<ActionEntry<WorldTarget>> entries = List.<ActionEntry<WorldTarget>>of(
            new SimpleQuickAction<WorldTarget>(
                    "world.open_folder",
                    WorldTarget::exists,
                    target -> ClientUtil.openPath(target.folder())),

            new SimpleQuickAction<WorldTarget>(
                    "world.copy_path",
                    WorldTarget::exists,
                    target -> ClientUtil.copyToClipboard(
                            target.folder().toAbsolutePath().toString(),
                            Component.translatable("quickactions.toast.path_copied"))),

            new SimpleQuickAction<WorldTarget>(
                    "world.duplicate",
                    WorldTarget::exists,
                    WorldOperations::duplicate),

            new QuickActionGroup<WorldTarget>("world.edit", List.<QuickAction<WorldTarget>>of(
                    new SimpleQuickAction<WorldTarget>(
                            "world.edit.rename",
                            WorldTarget::exists,
                            WorldOperations::rename),
                    new SimpleQuickAction<WorldTarget>(
                            "world.edit.change_icon",
                            WorldTarget::exists,
                            WorldOperations::changeIcon),
                    new SimpleQuickAction<WorldTarget>(
                            "world.edit.reset_icon",
                            WorldTarget::hasIcon,
                            WorldOperations::resetIcon))),

            new SimpleQuickAction<WorldTarget>(
                    "world.backup.create",
                    WorldTarget::exists,
                    BackupOperations::createBackup),

            new SimpleQuickAction<WorldTarget>(
                    "world.backup.restore",
                    target -> BackupManager.hasBackups(target.levelId()), // gray until there are backups
                    BackupOperations::openRestoreList),

            new SimpleQuickAction<WorldTarget>(
                    "world.delete",
                    WorldTarget::exists,
                    WorldOperations::delete)
    );

    @Override
    public ActionCategory category() {
        return ActionCategory.WORLD;
    }

    @Override
    public List<ActionEntry<WorldTarget>> entries() {
        return entries;
    }
}