package com.quickactions.actions.screenshot;

import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public record ScreenshotTarget(ScreenshotInfo info, Screen screen, Runnable onDeleted, Consumer<Path> onRenamed) {

    public boolean exists() {
        return Files.isRegularFile(info.file());
    }
}
