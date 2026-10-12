package com.quickactions.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Cursors {
    private Cursors() {
    }

    /** hand: cursor over clickable thing */
    public static void hand(GuiGraphicsExtractor g) {
        g.requestCursor(CursorTypes.POINTING_HAND);
    }

    /** up and down arrows: wheel autoscroll */
    public static void resizeVertical(GuiGraphicsExtractor g) {
        g.requestCursor(CursorTypes.RESIZE_NS);
    }
}
