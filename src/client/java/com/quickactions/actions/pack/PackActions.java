package com.quickactions.actions.pack;

import com.quickactions.api.ActionCategory;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.QuickAction;
import com.quickactions.api.QuickActionGroup;
import com.quickactions.api.SimpleQuickAction;

import java.util.List;

public final class PackActions implements ActionProvider<PackTarget> {

    private final List<ActionEntry<PackTarget>> entries = List.<ActionEntry<PackTarget>>of(
            new SimpleQuickAction<PackTarget>(
                    "pack.show_in_folder",
                    PackTarget::exists,
                    PackOperations::showInFolder),

            new SimpleQuickAction<PackTarget>(
                    "pack.copy_path",
                    PackTarget::exists,
                    PackOperations::copyPath),

            new SimpleQuickAction<PackTarget>(
                    "pack.move_top",
                    PackTarget::canMoveUp,
                    PackOperations::moveToTop),

            new SimpleQuickAction<PackTarget>(
                    "pack.move_bottom",
                    PackTarget::canMoveDown,
                    PackOperations::moveToBottom),

            new QuickActionGroup<PackTarget>("pack.edit", List.<QuickAction<PackTarget>>of(
                    new SimpleQuickAction<PackTarget>(
                            "pack.edit.rename",
                            PackTarget::editable,
                            PackOperations::rename),
                    new SimpleQuickAction<PackTarget>(
                            "pack.edit.description",
                            PackTarget::editable,
                            PackOperations::editDescription),
                    new SimpleQuickAction<PackTarget>(
                            "pack.edit.change_icon",
                            PackTarget::editable,
                            PackOperations::changeIcon),
                    new SimpleQuickAction<PackTarget>(
                            "pack.edit.reset_icon",
                            target -> target.editable() && target.hasIcon(),
                            PackOperations::resetIcon),
                    new SimpleQuickAction<PackTarget>(
                            "pack.edit.fix_compat",
                            PackTarget::canFixCompatibility,
                            PackOperations::fixCompatibility))),

            new SimpleQuickAction<PackTarget>(
                    "pack.duplicate",
                    PackTarget::exists,
                    PackOperations::duplicate),

            new SimpleQuickAction<PackTarget>(
                    "pack.delete",
                    PackTarget::editable,
                    PackOperations::delete)
    );

    @Override
    public ActionCategory category() {
        return ActionCategory.RESOURCE_PACK;
    }

    @Override
    public List<ActionEntry<PackTarget>> entries() {
        return entries;
    }
}