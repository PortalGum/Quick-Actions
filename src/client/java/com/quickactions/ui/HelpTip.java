package com.quickactions.ui;

import com.quickactions.ui.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class HelpTip {
    public static final int SIZE = 11;
    private static final int TOOLTIP_MAX_WIDTH = 230;

    private static final int MARKER_BG = 0x33FFFFFF;
    private static final int MARKER_BG_HOVER = 0x66FFFFFF;
    private static final int MARKER_TEXT_HOVER = 0xFFFFFFFF;

    private HelpTip() {
    }

    public static boolean isOver(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 1 && mouseX < x + SIZE + 1 && mouseY >= y - 1 && mouseY < y + SIZE + 1;
    }

    public static void drawMarker(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean hovered) {
        int background = hovered ? MARKER_BG_HOVER : MARKER_BG;
        graphics.fill(x + 2, y, x + SIZE - 2, y + SIZE, background);
        graphics.fill(x, y + 2, x + SIZE, y + SIZE - 2, background);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, background);

        int textWidth = font.width("?");
        graphics.text(font, Component.literal("?"),
                x + (SIZE - textWidth) / 2, y + (SIZE - font.lineHeight) / 2 + 1,
                hovered ? MARKER_TEXT_HOVER : Theme.muted(), true);
    }

    public static void renderTooltip(GuiGraphicsExtractor graphics, Font font, Component text,
                                     int mouseX, int mouseY, int screenWidth, int screenHeight) {
        List<String> lines = UiText.wrap(font, text.getString(), TOOLTIP_MAX_WIDTH);
        int lineStep = font.lineHeight + 2;

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int boxWidth = textWidth + 12;
        int boxHeight = lines.size() * lineStep + 4;

        int x = mouseX + 12;
        if (x + boxWidth > screenWidth - 4) {
            x = mouseX - boxWidth - 8;
        }
        x = Math.max(4, x);
        int y = Math.max(4, Math.min(mouseY + 12, screenHeight - boxHeight - 4));

        graphics.nextStratum();
        graphics.fill(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, Theme.accentSoft());
        graphics.fill(x, y, x + boxWidth, y + boxHeight, Theme.tooltipBg());
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, Component.literal(lines.get(i)), x + 6, y + 4 + i * lineStep, Theme.textBright(), true);
        }
    }
}