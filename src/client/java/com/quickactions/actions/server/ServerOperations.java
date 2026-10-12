package com.quickactions.actions.server;

import com.quickactions.config.ServerHideStore;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.util.ClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** all methods run on main thread on click */
public final class ServerOperations {
    private ServerOperations() {
    }

    private static void resetPing(ServerData data) {
        data.setState(ServerData.State.INITIAL);
    }

    public static void refresh(ServerTarget target) {
        resetPing(target.data());
    }

    public static void refreshAll(ServerListTarget target) {
        for (int i = 0; i < target.servers().size(); i++) {
            resetPing(target.servers().get(i));
        }
    }

    public static void copyIp(ServerTarget target) {
        ClientUtil.copyToClipboard(target.address(), Component.translatable("quickactions.toast.ip_copied"));
    }

    public static void rename(ServerTarget target) {
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                target.screen(),
                Component.translatable("quickactions.server.rename.title"),
                target.name(),
                128,
                newName -> {
                    target.data().name = newName;
                    target.saveAndRefresh();
                }));
    }

    public static void changeAddress(ServerTarget target) {
        String oldAddress = target.address();
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                target.screen(),
                Component.translatable("quickactions.server.address.title"),
                oldAddress,
                128,
                newAddress -> {
                    target.data().ip = newAddress;
                    ServerHideStore.move(oldAddress, newAddress);
                    resetPing(target.data());
                    target.saveAndRefresh();
                }));
    }

    public static void delete(ServerTarget target) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        target.servers().remove(target.data());
                        target.servers().save();
                    }
                    mc.setScreenAndShow(target.screen());
                    target.updateList().run();
                },
                Component.translatable("selectServer.deleteQuestion"),
                Component.translatable("selectServer.deleteWarning", target.name()),
                Component.translatable("selectServer.deleteButton"),
                CommonComponents.GUI_CANCEL));
    }
}