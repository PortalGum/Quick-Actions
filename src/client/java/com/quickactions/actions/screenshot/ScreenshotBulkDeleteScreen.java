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

import java.util.List;

public class ScreenshotBulkDeleteScreen extends Screen {
    private static final int BUTTON_WIDTH = 140;
    private static final int MAX_TILES = 5;
    private static final int TILE_MAX_WIDTH = 104;
    private static final int TILE_GAP = 6;
    private static final int TEXT_WIDTH = 300;

    private static final int COLOR_TITLE_DANGER = 0xFFFF7B7B;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFF8A3A3A;

    private final Screen parent;
    private final List<ScreenshotInfo> infos;
    private final Runnable onConfirm;
    private final long totalBytes;

    private List<String> messageLines = List.of();
    private int titleY;
    private int tilesY;
    private int tilesLeft;
    private int tileW;
    private int tileH;
    private int tileCount;
    private int summaryY;
    private int messageY;

    public ScreenshotBulkDeleteScreen(Screen parent, List<ScreenshotInfo> infos, Runnable onConfirm) {
        super(Component.translatable("quickactions.shots.delete_many.title"));
        this.parent = parent;
        this.infos = infos;
        this.onConfirm = onConfirm;
        long sum = 0;
        for (ScreenshotInfo info : infos) {
            sum += info.sizeBytes();
        }
        this.totalBytes = sum;
    }

    @Override
    protected void init() {
        int lh = this.font.lineHeight;
        messageLines = UiText.wrap(this.font,
                Component.translatable("quickactions.shots.delete_many.message").getString(), TEXT_WIDTH);
        int messageHeight = messageLines.size() * (lh + 2);

        tileCount = Math.min(MAX_TILES, infos.size());
        tileW = Math.min(TILE_MAX_WIDTH, (this.width - 40 - (tileCount - 1) * TILE_GAP) / tileCount);
        tileH = tileW * 9 / 16;
        int fixed = lh + 12 + 10 + lh + 8 + messageHeight + 14 + 20;
        int available = this.height - 24 - fixed;
        if (tileH > available) {
            tileH = Math.max(24, available);
            tileW = tileH * 16 / 9;
        }

        int rowWidth = tileCount * tileW + (tileCount - 1) * TILE_GAP;
        tilesLeft = this.width / 2 - rowWidth / 2;

        int total = fixed + tileH;
        int top = Math.max(12, (this.height - total) / 2);
        titleY = top;
        tilesY = top + lh + 12;
        summaryY = tilesY + tileH + 10;
        messageY = summaryY + lh + 8;
        int buttonY = messageY + messageHeight + 14;

        int left = this.width / 2 - BUTTON_WIDTH - 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("quickactions.shots.delete_many.confirm", infos.size()), button -> {
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

        for (int i = 0; i < tileCount; i++) {
            drawTile(g, i, tilesLeft + i * (tileW + TILE_GAP), tilesY);
        }

        Component summary = Component.translatable("quickactions.shots.delete_many.summary",
                infos.size(), UiText.formatBytes(totalBytes));
        g.text(this.font, summary, centerX - this.font.width(summary) / 2, summaryY, COLOR_WHITE, true);

        int y = messageY;
        for (String line : messageLines) {
            g.text(this.font, Component.literal(line), centerX - this.font.width(line) / 2, y, Theme.textSoft(), true);
            y += this.font.lineHeight + 2;
        }
    }

    private void drawTile(GuiGraphicsExtractor g, int index, int x, int y) {
        g.fill(x - 1, y - 1, x + tileW + 1, y + tileH + 1, COLOR_BORDER);
        g.fill(x, y, x + tileW, y + tileH, Theme.deep());

        ScreenshotInfo info = infos.get(index);
        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        int pxW = ((tileW * scale + 31) / 32) * 32;
        int pxH = Math.max(1, Math.round(pxW * tileH / (float) tileW));
        Loaded loaded = ScreenshotTextures.get(info, Mode.FILL, pxW, pxH);
        if (loaded != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, loaded.id(), x, y, 0, 0, tileW, tileH, tileW, tileH);
        } else {
            Loaded fallback = ScreenshotTextures.fallback(info);
            if (fallback != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, fallback.id(), x, y, 0, 0, tileW, tileH, tileW, tileH);
            }
        }

        int hidden = infos.size() - tileCount;
        if (hidden > 0 && index == tileCount - 1) {
            g.fill(x, y, x + tileW, y + tileH, 0xB0000000);
            String more = "+" + (hidden + 1);
            g.text(this.font, Component.literal(more), x + (tileW - this.font.width(more)) / 2,
                    y + (tileH - this.font.lineHeight) / 2, COLOR_WHITE, true);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
