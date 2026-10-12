package com.quickactions.ui.notify;

import com.quickactions.ui.theme.Theme;

import com.quickactions.QuickActionsClient;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.UiText;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class NotificationManager {
    private static final int WIDTH = 236;
    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final int PADDING = 8;
    private static final int STRIPE = 3;
    private static final int BAR_HEIGHT = 3;
    private static final int BUTTON_HEIGHT = 16;
    private static final int MAX_VISIBLE = 4;
    private static final long SLIDE_IN_MS = 260;
    private static final long FADE_OUT_MS = 450;

    private static final int COLOR_SHADOW = 0x55000000;
    private static final int COLOR_TRACK = 0x33FFFFFF;
    private static final int COLOR_TITLE = 0xFFFFFFFF;

    private static final List<Notification> ACTIVE = new ArrayList<>();
    private static long lastFrameNanos;

    private NotificationManager() {
    }

    public static void show(Notification notification) {
        notification.createdNanos = System.nanoTime();
        ACTIVE.add(notification);
        while (ACTIVE.size() > MAX_VISIBLE) {
            ACTIVE.remove(0);
        }
    }

    public static void install(Screen screen) {
        ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
                render(graphics, mouseX, mouseY, s.width, s.height));
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                !handleClick(event.x(), event.y(), event.button()));
    }

    // ---------- sizes ----------

    private static int heightOf(Font font, Notification n) {
        int lh = font.lineHeight;
        int h = PADDING + lh;
        if (n.message != null) h += 3 + lh;
        h += n.rows.size() * (3 + lh);
        if (n.detail != null) h += 3 + lh;
        if (n.actionLabel != null) h += 6 + BUTTON_HEIGHT;
        return h + PADDING + BAR_HEIGHT;
    }

    private static boolean contains(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    // ---------- render ----------

    private static void render(GuiGraphicsExtractor g, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (ACTIVE.isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        long now = System.nanoTime();
        long dt = lastFrameNanos == 0 ? 0 : Math.min(now - lastFrameNanos, 100_000_000L);
        lastFrameNanos = now;

        g.nextStratum(); // above screen widgets

        int cursor = screenHeight - MARGIN;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) { // new at the bottom, old above
            Notification n = ACTIVE.get(i);
            int height = heightOf(font, n);
            int restX = screenWidth - MARGIN - WIDTH;
            int y = cursor - height;
            cursor = y - GAP;

            boolean hovered = contains(mouseX, mouseY, restX, y, WIDTH, height);
            if (hovered && n.elapsedMs(now) < n.durationMs) {
                n.pausedNanos += dt;
            }
            long elapsed = n.elapsedMs(now);

            float alpha = elapsed > n.durationMs
                    ? 1f - clamp01((elapsed - n.durationMs) / (float) FADE_OUT_MS)
                    : 1f;
            float slide = clamp01(elapsed / (float) SLIDE_IN_MS);
            float eased = 1f - (1f - slide) * (1f - slide) * (1f - slide);
            int x = restX
                    + Math.round((1f - eased) * (WIDTH + MARGIN)) // slides in from the right
                    + Math.round((1f - alpha) * 24f);             // slides out to the right when fading

            n.drawn = true;
            n.alpha = alpha;
            n.drawX = x;
            n.drawY = y;
            n.drawHeight = height;

            if (alpha >= 0.04f) {
                draw(g, font, n, x, y, height, elapsed, alpha, mouseX, mouseY);
            }
        }

        ACTIVE.removeIf(n -> n.elapsedMs(now) >= n.durationMs + FADE_OUT_MS);
    }

    private static void draw(GuiGraphicsExtractor g, Font font, Notification n, int x, int y, int height,
                             long elapsed, float alpha, int mouseX, int mouseY) {
        g.fill(x + 2, y + 2, x + WIDTH + 2, y + height + 2, fade(COLOR_SHADOW, alpha));
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, fade(Theme.border(), alpha));
        g.fill(x, y, x + WIDTH, y + height, fade(Theme.panelBg(), alpha));
        g.fill(x, y, x + STRIPE, y + height, fade(n.type.color(), alpha));

        int textX = x + STRIPE + PADDING;
        int maxTextWidth = WIDTH - STRIPE - PADDING * 2;
        int lh = font.lineHeight;
        int ty = y + PADDING;

        g.text(font, n.title, textX, ty, fade(COLOR_TITLE, alpha), true);
        ty += lh;

        if (n.message != null) {
            ty += 3;
            String line = UiText.fitEnd(font, n.message.getString(), maxTextWidth);
            g.text(font, Component.literal(line), textX, ty, fade(Theme.textSoft(), alpha), true);
            ty += lh;
        }

        for (Component row : n.rows) {
            ty += 3;
            g.text(font, row, textX, ty, fade(Theme.textSoft(), alpha), true);
            ty += lh;
        }

        if (n.detail != null) {
            ty += 3;
            String line = UiText.fitStart(font, n.detail, maxTextWidth);
            g.text(font, Component.literal(line), textX, ty, fade(Theme.accentLight(), alpha), true);
            ty += lh;
        }

        n.buttonWidth = 0;
        if (n.actionLabel != null) {
            ty += 6;
            int bw = font.width(n.actionLabel) + 18;
            boolean over = contains(mouseX, mouseY, textX, ty, bw, BUTTON_HEIGHT);
            g.fill(textX - 1, ty - 1, textX + bw + 1, ty + BUTTON_HEIGHT + 1,
                    fade(over ? Theme.accent() : Theme.borderStrong(), alpha));
            g.fill(textX, ty, textX + bw, ty + BUTTON_HEIGHT,
                    fade(over ? Theme.accentDeep() : Theme.cardHover(), alpha));
            g.text(font, n.actionLabel, textX + (bw - font.width(n.actionLabel)) / 2,
                    ty + (BUTTON_HEIGHT - lh) / 2, fade(COLOR_TITLE, alpha), true);
            n.buttonX = textX;
            n.buttonY = ty;
            n.buttonWidth = bw;
        }

        int barY = y + height - BAR_HEIGHT;
        int barWidth = WIDTH - STRIPE;
        float progress = clamp01(elapsed / (float) Math.max(1, n.durationMs));
        g.fill(x + STRIPE, barY, x + WIDTH, barY + BAR_HEIGHT, fade(COLOR_TRACK, alpha));
        g.fill(x + STRIPE, barY, x + STRIPE + Math.round(barWidth * progress), barY + BAR_HEIGHT,
                fade(n.type.color(), alpha));
    }

    // ---------- clicks ----------

    private static boolean handleClick(double mx, double my, int button) {
        long now = System.nanoTime();
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Notification n = ACTIVE.get(i);
            // ignore fading cards
            if (!n.drawn || n.elapsedMs(now) >= n.durationMs) continue;
            if (!contains(mx, my, n.drawX, n.drawY, WIDTH, n.drawHeight)) continue;

            if (button == MouseButtons.LEFT && n.action != null && n.buttonWidth > 0
                    && contains(mx, my, n.buttonX, n.buttonY, n.buttonWidth, BUTTON_HEIGHT)) {
                try {
                    n.action.run();
                } catch (Exception e) {
                    QuickActionsClient.LOGGER.error("Notification action failed", e);
                }
            }
            n.dismiss(); // click on button or card closes it
            return true;
        }
        return false;
    }
}