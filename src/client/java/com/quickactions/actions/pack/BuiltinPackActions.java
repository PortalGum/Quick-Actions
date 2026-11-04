package com.quickactions.actions.pack;

import com.quickactions.actions.pack.preset.PackSetup;
import com.quickactions.api.ActionCategory;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.SimpleQuickAction;

import java.util.List;

public final class BuiltinPackActions implements ActionProvider<BuiltinPackTarget> {

    private final List<ActionEntry<BuiltinPackTarget>> entries = List.<ActionEntry<BuiltinPackTarget>>of(
            new SimpleQuickAction<BuiltinPackTarget>(
                    "pack.move_top",
                    BuiltinPackTarget::canMoveUp,
                    target -> PackSetup.moveToTop(target.model())),

            new SimpleQuickAction<BuiltinPackTarget>(
                    "pack.move_bottom",
                    BuiltinPackTarget::canMoveDown,
                    target -> PackSetup.moveToBottom(target.model()))
    );

    @Override
    public ActionCategory category() {
        return ActionCategory.RESOURCE_PACK;
    }

    @Override
    public List<ActionEntry<BuiltinPackTarget>> entries() {
        return entries;
    }
}