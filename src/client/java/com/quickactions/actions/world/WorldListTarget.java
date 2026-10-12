package com.quickactions.actions.world;

import net.minecraft.client.gui.screens.Screen;

public record WorldListTarget(Screen screen, Runnable refresh) {
}