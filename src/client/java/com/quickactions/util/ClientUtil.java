package com.quickactions.util;

import com.mojang.blaze3d.Blaze3D;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLMisc;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientUtil {
    private ClientUtil() {
    }

    /** opens a folder/file with the game's own api (Blaze3D.openPath, same thing vanilla "Open Pack Folder" uses) */
    public static boolean openPath(Path path) {
        try {
            Blaze3D.openPath(path.toAbsolutePath());
            return true;
        } catch (Exception e) {
            Notifications.failure("Open " + path, e);
            return false;
        }
    }

    /** opens a link through sdl, main thread only, only called from a button click with a fixed url */
    public static void openUrl(String url) {
        try {
            if (!SDLMisc.SDL_OpenURL(url)) {
                throw new IllegalStateException("SDL_OpenURL failed");
            }
        } catch (Exception e) {
            Notifications.failure("Open link " + url, e);
        }
    }

    public static boolean openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (Exception e) {
            Notifications.failure("Create folder " + folder, e);
            return false;
        }
        return openPath(folder);
    }

    /** opens the folder the file is in (selecting the file needs a system tool, so no) */
    public static void showInFolder(Path path) {
        Path abs = path.toAbsolutePath();
        Path parent = abs.getParent();
        openPath(parent != null ? parent : abs);
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