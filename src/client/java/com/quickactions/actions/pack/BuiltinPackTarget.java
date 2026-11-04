package com.quickactions.actions.pack;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;

public record BuiltinPackTarget(PackSelectionModel.Entry model) {

    public boolean canMoveUp() {
        return model.isSelected() && model.canMoveUp();
    }

    public boolean canMoveDown() {
        return model.isSelected() && model.canMoveDown();
    }
}