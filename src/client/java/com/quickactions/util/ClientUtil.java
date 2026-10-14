package com.quickactions.util;

import com.quickactions.QuickActionsClient;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ClientUtil {
    private ClientUtil() {
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean mac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /** open folder/file in OS explorer; tells the player if nothing worked */
    public static boolean openPath(Path path) {
        String target = path.toAbsolutePath().toString();
        try {
            if (windows()) {
                new ProcessBuilder(PowerShell.systemExe("explorer.exe"), target).start();
            } else if (mac()) {
                new ProcessBuilder("open", target).start();
            } else {
                new ProcessBuilder("xdg-open", target).start();
            }
            return true;
        } catch (Exception e) {
            QuickActionsClient.LOGGER.warn("Primary way to open {} failed, trying java.awt.Desktop", path, e);
        }
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(new File(target));
                return true;
            }
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Desktop.open failed: {}", path, e);
        }
        QuickActionsClient.LOGGER.error("Could not open path in the file manager: {}", path);
        reportFailure();
        return false;
    }

    /** open link in default browser */
    public static void openUrl(String url) {
        try {
            if (windows()) {
                new ProcessBuilder(PowerShell.systemExe("System32\\rundll32.exe"), "url.dll,FileProtocolHandler", url).start();
            } else if (mac()) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to open url: {}", url, e);
            reportFailure();
        }
    }

    public static boolean openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Failed to create folder: {}", folder, e);
            return false;
        }
        return openPath(folder);
    }

    /** select the file in the file manager (Windows); elsewhere open the parent folder */
    public static void showInFolder(Path path) {
        Path abs = path.toAbsolutePath();
        if (!windows()) {
            Path parent = abs.getParent();
            openPath(parent != null ? parent : abs);
            return;
        }
        try {
            new ProcessBuilder(PowerShell.systemExe("explorer.exe"), "/select,", abs.toString()).start();
        } catch (Exception e) {
            QuickActionsClient.LOGGER.warn("explorer /select failed for {}, opening parent folder", path, e);
            Path parent = abs.getParent();
            openPath(parent != null ? parent : abs);
        }
    }

    private static void reportFailure() {
        Minecraft.getInstance().execute(Notifications::failure);
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