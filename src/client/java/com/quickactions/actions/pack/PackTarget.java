package com.quickactions.actions.pack;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;

import java.nio.file.Files;
import java.nio.file.Path;

public record PackTarget(String id, String fileName, Path path, PackSelectionModel.Entry model,
                         boolean active, boolean incompatible, Screen screen) {

    public boolean exists() {
        return Files.exists(path);
    }

    public boolean editable() {
        return !active && exists();
    }

    public boolean hasIcon() {
        return PackArchive.hasEntry(path, PackArchive.ICON);
    }

    public boolean canFixCompatibility() {
        return editable() && incompatible;
    }

    public boolean canMoveUp() {
        return model.isSelected() && model.canMoveUp();
    }

    public boolean canMoveDown() {
        return model.isSelected() && model.canMoveDown();
    }
}