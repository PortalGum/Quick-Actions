package com.quickactions.actions.server;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerList;

public record ServerListTarget(ServerList servers, Screen screen, Runnable updateList) {
}