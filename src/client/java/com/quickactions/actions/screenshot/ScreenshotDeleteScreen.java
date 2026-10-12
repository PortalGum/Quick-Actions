package com.quickactions.actions.screenshot;

import com.quickactions.ui.theme.Theme;

import com.quickactions.actions.screenshot.ScreenshotTextures.Loaded;
import com.quickactions.actions.screenshot.ScreenshotTextures.Mode;
import com.quickactions.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ScreenshotDeleteScreen extends Screen {
    private static final int BUTTON_WIDTH = 120;
    private static final int PREVIEW_MAX_WIDTH = 260;
    private static final int TEXT_WIDTH = 300;

    private static final int COLOR_TITLE_DANGER = 0xFFFF7B7B;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFF8A3A3A;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Screen parent;
    private final ScreenshotInfo info;
    private final Runnable onConfirm;

    private List<String> messageLines = List.of();
    private int titleY;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int nameY;
    private int messageY;

    public ScreenshotDeleteScreen(Screen parent, ScreenshotInfo info, Runnable onConfirm) {
        super(Component.translatable("quickactions.shots.delete.title"));
        this.parent = parent;
        this.info = info;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int lh = this.font.lineHeight;
        messageLines = UiText.wrap(this.font,
                Component.translatable("quickactions.shots.delete.message").getString(), TEXT_WIDTH);
        int messageHeight = messageLines.size() * (lh + 2);

        previewW = Math.min(PREVIEW_MAX_WIDTH, this.width - 40);
        previewH = previewW * 9 / 16;
        int fixed = lh + 12 + 8 + (lh + 3) * 2 + 8 + messageHeight + 14 + 20;
        int available = this.height - 24 - fixed;
        if (previewH > available) {
            previewH = Math.max(36, available);
            previewW = previewH * 16 / 9;
        }

        int total = fixed + previewH;
        int top = Math.max(12, (this.height - total) / 2);
        titleY = top;
        previewY = top + lh + 12;
        previewX = this.width / 2 - previewW / 2;
        nameY = previewY + previewH + 8;
        messageY = nameY + (lh + 3) * 2 + 8;
        int buttonY = messageY + messageHeight + 14;

        int left = this.width / 2 - BUTTON_WIDTH - 2;
        addRenderableWidget(Button.builder(Component.translatable("quickactions.shots.delete.confirm"), button -> {
                    Minecraft.getInstance().setScreenAndShow(parent);
                    onConfirm.run();
                })
                .bounds(left, buttonY, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + BUTTON_WIDTH + 4, buttonY, BUTTON_WIDTH, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        g.text(this.font, this.title, centerX - this.font.width(this.title) / 2, titleY, COLOR_TITLE_DANGER, true);
        int lineY = titleY + this.font.lineHeight + 3;
        g.fill(centerX - 30, lineY, centerX + 30, lineY + 1, COLOR_TITLE_DANGER);

        drawPreview(g);

        String name = UiText.fitEnd(this.font, info.name(), this.width - 40);
        g.text(this.font, Component.literal(name), centerX - this.font.width(name) / 2, nameY, COLOR_WHITE, true);
        String details = info.time().format(TIME_FORMAT) + "  ·  " + UiText.formatBytes(info.sizeBytes());
        g.text(this.font, Component.literal(details), centerX - this.font.width(details) / 2,
                nameY + this.font.lineHeight + 3, Theme.muted(), true);

        int y = messageY;
        for (String line : messageLines) {
            g.text(this.font, Component.literal(line), centerX - this.font.width(line) / 2, y, Theme.textSoft(), true);
            y += this.font.lineHeight + 2;
        }
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        g.fill(previewX - 1, previewY - 1, previewX + previewW + 1, previewY + previewH + 1, COLOR_BORDER);
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, Theme.deep());

        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        Loaded loaded = ScreenshotTextures.get(info, Mode.FIT, previewW * scale, previewH * scale);
        if (loaded != null) {
            int w = Math.min(previewW, loaded.width() / scale);
            int h = Math.min(previewH, loaded.height() / scale);
            g.blit(RenderPipelines.GUI_TEXTURED, loaded.id(),
                    previewX + (previewW - w) / 2, previewY + (previewH - h) / 2, 0, 0, w, h, w, h);
            return;
        }
        Loaded fallback = ScreenshotTextures.fallback(info);
        if (fallback != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, fallback.id(), previewX, previewY, 0, 0,
                    previewW, previewH, previewW, previewH);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
