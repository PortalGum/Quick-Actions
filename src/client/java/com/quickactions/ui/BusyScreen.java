package com.quickactions.ui;

import com.quickactions.ui.theme.Theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BusyScreen extends Screen {
    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 4;
    private static final int SEGMENT = 48;

    private final Component message;

    public BusyScreen(Component title, Component message) {
        super(title);
        this.message = message;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        // can't close until the operation ends
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2,
                centerY - 22, 0xFFFFFFFF, true);
        graphics.text(this.font, this.message, centerX - this.font.width(this.message) / 2,
                centerY - 8, Theme.muted(), true);

        int x = centerX - BAR_WIDTH / 2;
        int y = centerY + 12;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, Theme.border());
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, Theme.card());

        float phase = (System.currentTimeMillis() % 1400L) / 1400f;
        int position = (int) ((BAR_WIDTH + SEGMENT) * phase) - SEGMENT;
        int start = Math.max(0, position);
        int end = Math.min(BAR_WIDTH, position + SEGMENT);
        if (end > start) {
            graphics.fill(x + start, y, x + end, y + BAR_HEIGHT, Theme.accent());
        }
    }
}