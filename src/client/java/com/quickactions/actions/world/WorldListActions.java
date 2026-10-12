package com.quickactions.actions.world;

import com.quickactions.actions.world.backup.BackupOperations;
import com.quickactions.api.ActionCategory;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.SimpleQuickAction;

import java.util.List;

/** common actions for the whole world list */
public final class WorldListActions implements ActionProvider<WorldListTarget> {

    private final List<ActionEntry<WorldListTarget>> entries = List.<ActionEntry<WorldListTarget>>of(
            new SimpleQuickAction<WorldListTarget>(
                    "world.global.backups",
                    target -> true,
                    BackupOperations::openAllBackups)
    );

    @Override
    public ActionCategory category() {
        return ActionCategory.WORLD;
    }

    @Override
    public List<ActionEntry<WorldListTarget>> entries() {
        return entries;
    }
}