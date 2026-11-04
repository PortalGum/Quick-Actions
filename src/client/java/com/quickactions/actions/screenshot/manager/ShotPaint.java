package com.quickactions.actions.screenshot.manager;

import com.quickactions.ui.Icons;
import com.quickactions.ui.UiText;
import com.quickactions.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** colors and small draw helpers shared by the manager parts */
final class ShotPaint {
    static final int WHITE = 0xFFFFFFFF;
    private static final int BROKEN_TILE = 0xFF2A1618;
    private static final int BROKEN_TEXT = 0xFFFF8A8A;

    private ShotPaint() {
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    static int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    static float clampF(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static int lerpColor(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - k) + ((to >>> 24) & 0xFF) * k);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int gr = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** rgb + alpha 0..1 -> argb */
    static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /** rounded plate, 3 fills that never overlap so translucent colors look even */
    static void fillPlate(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
    }

    /** rounded 1px border with a fill inside */
    static void roundBox(GuiGraphicsExtractor g, Rect r, int border, int fill) {
        g.fill(r.x() + 1, r.y(), r.x() + r.w() - 1, r.y() + r.h(), border);
        g.fill(r.x(), r.y() + 1, r.x() + r.w(), r.y() + r.h() - 1, border);
        g.fill(r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, fill);
    }

    static void shimmer(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Theme.tone(0.022f));
        float phase = (System.currentTimeMillis() % 1500L) / 1500f;
        int band = Math.max(8, w / 3);
        int bandX = x - band + (int) ((w + band * 2) * phase);
        int from = Math.max(x, bandX);
        int to = Math.min(x + w, bandX + band);
        if (to > from) g.fill(from, y, to, y + h, 0x14FFFFFF);
    }

    static void broken(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BROKEN_TILE);
        g.text(font, Component.literal("?"), x + (w - font.width("?")) / 2,
                y + (h - font.lineHeight) / 2, BROKEN_TEXT, true);
    }

    /** camera icon, title and hint centered in a panel (empty and error states) */
    static void centered(GuiGraphicsExtractor g, Font font, int left, int width, int centerY,
                         Component title, Component hint) {
        Icons.camera(g, left + width / 2 - Icons.CAMERA_WIDTH / 2,
                centerY - 34, Theme.scroll(), Theme.panelBg(), Theme.scroll());
        g.text(font, title, left + (width - font.width(title)) / 2, centerY - 12, WHITE, true);
        String hintText = UiText.fitEnd(font, hint.getString(), width - 24);
        g.text(font, Component.literal(hintText), left + (width - font.width(hintText)) / 2,
                centerY + 2, Theme.muted(), true);
    }
}
