package com.quickactions.actions.pack;

import com.quickactions.actions.pack.preset.PackSetup;
import com.quickactions.api.ActionCategory;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.SimpleQuickAction;

import java.util.List;

/** menu for empty space in pack lists */
public final class PackListActions implements ActionProvider<PackListTarget> {

    private final List<ActionEntry<PackListTarget>> entries = List.<ActionEntry<PackListTarget>>of(
            new SimpleQuickAction<PackListTarget>(
                    "pack.global.presets",
                    target -> true,
                    PackListOperations::openPresets),

            new SimpleQuickAction<PackListTarget>(
                    "pack.global.save_preset",
                    target -> PackSetup.countDisableable(target.screen()) > 0, // gray if no enabled packs
                    PackListOperations::savePreset),

            new SimpleQuickAction<PackListTarget>(
                    "pack.global.disable_all",
                    target -> PackSetup.countDisableable(target.screen()) > 0, // gray if nothing to disable
                    PackListOperations::disableAll)
    );

    @Override
    public ActionCategory category() {
        return ActionCategory.RESOURCE_PACK;
    }

    @Override
    public List<ActionEntry<PackListTarget>> entries() {
        return entries;
    }
}