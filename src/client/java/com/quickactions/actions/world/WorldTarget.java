package com.quickactions.actions.world;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;

import java.nio.file.Files;
import java.nio.file.Path;

public record WorldTarget(
        String levelId,
        String displayName,
        Screen screen,
        Runnable refresh,
        WorldSelectionList.WorldListEntry entry) {

    public Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("saves").resolve(levelId);
    }

    public boolean exists() {
        return Files.isDirectory(folder());
    }

    public Path iconFile() {
        return folder().resolve("icon.png");
    }

    public boolean hasIcon() {
        return Files.isRegularFile(iconFile());
    }
}