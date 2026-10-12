package com.quickactions.actions.server;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

public record ServerTarget(ServerData data, ServerList servers, Screen screen, Runnable updateList) {

    public String name() {
        return data.name;
    }

    public String address() {
        return data.ip;
    }

    /** save servers.dat and redraw list */
    public void saveAndRefresh() {
        servers.save();
        updateList.run();
    }
}