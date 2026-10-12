package com.quickactions.util;

import com.quickactions.QuickActionsClient;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ClientUtil {
    private ClientUtil() {
    }

    /** open folder/file in OS explorer */
    public static void openPath(Path path) {
        try {
            String target = path.toAbsolutePath().toString();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", target).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", target).start();
            } else {
                new ProcessBuilder("xdg-open", target).start();
            }
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to open path: {}", path, e);
        }
    }

    /** open link in default browser */
    public static void openUrl(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to open url: {}", url, e);
        }
    }

    public static boolean openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to create folder: {}", folder, e);
            return false;
        }
        openPath(folder);
        return true;
    }

    public static void showInFolder(Path path) {
        try {
            new ProcessBuilder("explorer.exe", "/select,", path.toAbsolutePath().toString()).start();
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to show in folder: {}", path, e);
        }
    }

    public static void copyToClipboard(String text, Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(text);
        Notification.success(Component.translatable("quickactions.toast.copied.title"))
                .message(message)
                .duration(2500)
                .show();
    }

    public static void toast(Component message) {
        Notification.info(Component.translatable("quickactions.toast.title"))
                .message(message)
                .duration(3000)
                .show();
    }

    public static void fail() {
        Notifications.failure();
    }

    public static void showAlert(Screen returnTo, Component title, Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new AlertScreen(() -> mc.setScreenAndShow(returnTo), title, message));
    }
}