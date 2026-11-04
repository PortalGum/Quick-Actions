package com.quickactions.actions.screenshot.manager;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** one item of the gallery layout: a day header (shot &lt; 0) or a screenshot card */
record Cell(int shot, @Nullable Component label, int count, int x, int y, int w, int h) {
    boolean isHeader() {
        return shot < 0;
    }
}
