package com.quickactions.actions.pack;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.pack.preset.PackPreset;
import com.quickactions.actions.pack.preset.PackSetup;
import com.quickactions.actions.pack.preset.PresetStore;
import com.quickactions.actions.pack.preset.PresetsScreen;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class PackListOperations {
    private PackListOperations() {
    }

    public static void openPresets(PackListTarget target) {
        Minecraft.getInstance().setScreenAndShow(new PresetsScreen(target.screen()));
    }

    public static void savePreset(PackListTarget target) {
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                target.screen(),
                Component.translatable("quickactions.preset.save.title"),
                Component.translatable("quickactions.preset.default_name").getString(),
                48,
                name -> saveCurrent(target.screen(), name)));
    }

    public static @Nullable PackPreset saveCurrent(Screen packScreen, String name) {
        try {
            PackPreset preset = PresetStore.create(name, PackSetup.captureCurrent(packScreen));
            Notification.success(Component.translatable("quickactions.notify.preset.saved.title"))
                    .message(Component.translatable("quickactions.notify.preset.saved.message",
                            preset.name(), preset.packs().size()))
                    .show();
            return preset;
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to save preset", e);
            Notifications.failure();
            return null;
        }
    }

    public static void disableAll(PackListTarget target) {
        int count = PackSetup.disableAll(target.screen());
        Notification.success(Component.translatable("quickactions.notify.pack.disabled.title"))
                .message(Component.translatable("quickactions.notify.pack.disabled.message", count))
                .detail(Component.translatable("quickactions.notify.pack.press_done").getString())
                .show();
    }
}