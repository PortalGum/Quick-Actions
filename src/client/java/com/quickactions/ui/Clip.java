package com.quickactions.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Clip {
    private Clip() {
    }

    public static void begin(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public static void end(GuiGraphicsExtractor g) {
        g.disableScissor();
    }
}
