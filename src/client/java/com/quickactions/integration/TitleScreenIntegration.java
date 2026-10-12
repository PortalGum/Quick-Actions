package com.quickactions.integration;

import com.quickactions.ui.theme.Theme;
import com.quickactions.ui.theme.Colors;

import com.quickactions.actions.screenshot.ScreenshotManagerScreen;
import com.quickactions.config.QuickActionsConfig;
import com.quickactions.ui.HelpTip;
import com.quickactions.ui.Icons;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.settings.SettingsScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class TitleScreenIntegration {
    private static final int MARGIN_X = 8;
    private static final int MARGIN_Y = 8;
    private static final int HEIGHT = 20;
    private static final int PADDING = 9;
    private static final int ICON_GAP = 7;
    private static final int BUTTON_GAP = 4;
    private static final int GEAR_RADIUS = 6;

    private static final int TEXT_HOVER = 0xFFFFFFFF;

    private static float hover;
    private static float gearHover;
    private static float gearAngle;
    private static long lastNanos;

    private TitleScreenIntegration() {
    }

    public static void tryAttach(Screen screen) {
        if (!(screen instanceof TitleScreen)) return;

        ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
                render(s, graphics, mouseX, mouseY));
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                !onClick(s, event.x(), event.y(), event.button()));
    }

    private static Component label() {
        return Component.translatable("quickactions.shots.title");
    }

    private static boolean shotsVisible() {
        return QuickActionsConfig.get().showScreenshotButton;
    }

    private static int shotsWidth(Font font) {
        return PADDING + Icons.CAMERA_WIDTH + ICON_GAP + font.width(label()) + PADDING;
    }

    private static int gearX(Font font) {
        return shotsVisible() ? MARGIN_X + shotsWidth(font) + BUTTON_GAP : MARGIN_X;
    }

    private static boolean insideShots(Font font, double mx, double my) {
        return shotsVisible() && mx >= MARGIN_X && mx < MARGIN_X + shotsWidth(font)
                && my >= MARGIN_Y && my < MARGIN_Y + HEIGHT;
    }

    private static boolean insideGear(Font font, double mx, double my) {
        int x = gearX(font);
        return mx >= x && mx < x + HEIGHT && my >= MARGIN_Y && my < MARGIN_Y + HEIGHT;
    }

    private static void render(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min((now - lastNanos) / 1_000_000_000f, 0.1f);
        lastNanos = now;
        float follow = Math.min(1f, dt * 14f);

        boolean overGear = insideGear(font, mouseX, mouseY);
        hover += ((insideShots(font, mouseX, mouseY) ? 1f : 0f) - hover) * follow;
        gearHover += ((overGear ? 1f : 0f) - gearHover) * follow;
        gearAngle += dt * (0.5f + 5f * gearHover); // always spins slowly, speeds up on hover

        if (shotsVisible()) drawShotsButton(g, font);
        drawGearButton(g, font);

        if (overGear) {
            HelpTip.renderTooltip(g, font, Component.translatable("quickactions.settings.tooltip"),
                    mouseX, mouseY, screen.width, screen.height);
        }
    }

    private static void drawShotsButton(GuiGraphicsExtractor g, Font font) {
        int w = shotsWidth(font);
        int x = MARGIN_X;
        int y = MARGIN_Y;
        float t = hover;

        g.fill(x + 2, y + 2, x + w + 2, y + HEIGHT + 2, 0x55000000);
        g.fill(x - 1, y - 1, x + w + 1, y + HEIGHT + 1, lerp(Theme.border(), Theme.accent(), t));
        g.fill(x, y, x + w, y + HEIGHT, lerp(Theme.surfaceAlpha(0xE0), Colors.withAlpha(Theme.accentMix(0.2f), 0xF0), t));
        g.fill(x, y, x + 2, y + HEIGHT, lerp(Theme.accentSoft(), Theme.accent(), t));

        Icons.camera(g, x + PADDING, y + (HEIGHT - Icons.CAMERA_HEIGHT) / 2,
                lerp(Theme.subtle(), 0xFFFFFFFF, t), Theme.panelOpaque(), Theme.accent());
        g.text(font, label(), x + PADDING + Icons.CAMERA_WIDTH + ICON_GAP,
                y + (HEIGHT - font.lineHeight) / 2 + 1, lerp(Theme.text(), TEXT_HOVER, t), true);
    }

    private static void drawGearButton(GuiGraphicsExtractor g, Font font) {
        int x = gearX(font);
        int y = MARGIN_Y;
        float t = gearHover;

        g.fill(x + 2, y + 2, x + HEIGHT + 2, y + HEIGHT + 2, 0x55000000);
        g.fill(x - 1, y - 1, x + HEIGHT + 1, y + HEIGHT + 1, lerp(Theme.border(), Theme.accent(), t));
        g.fill(x, y, x + HEIGHT, y + HEIGHT, lerp(Theme.surfaceAlpha(0xE0), Colors.withAlpha(Theme.accentMix(0.2f), 0xF0), t));
        Icons.gear(g, x + HEIGHT / 2, y + HEIGHT / 2, GEAR_RADIUS, gearAngle, lerp(Theme.subtle(), 0xFFFFFFFF, t));
    }

    private static boolean onClick(Screen screen, double mx, double my, int button) {
        if (button != MouseButtons.LEFT) return false;
        Font font = Minecraft.getInstance().font;
        if (insideGear(font, mx, my)) {
            Minecraft.getInstance().setScreenAndShow(new SettingsScreen(screen));
            return true;
        }
        if (insideShots(font, mx, my)) {
            Minecraft.getInstance().setScreenAndShow(new ScreenshotManagerScreen(screen));
            return true;
        }
        return false;
    }

    private static int lerp(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - k) + ((to >>> 24) & 0xFF) * k);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
