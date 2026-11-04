package com.quickactions.actions.screenshot.manager;

import com.quickactions.actions.screenshot.ScreenshotInfo;
import com.quickactions.actions.screenshot.ScreenshotTextures;
import com.quickactions.actions.screenshot.ScreenshotTextures.Loaded;
import com.quickactions.actions.screenshot.ScreenshotTextures.Mode;
import com.quickactions.ui.UiText;
import com.quickactions.ui.theme.Theme;
import com.quickactions.util.FileOps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

import static com.quickactions.actions.screenshot.manager.GalleryLayout.DETAIL_WIDTH;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.PANEL_TOP;
import static com.quickactions.actions.screenshot.manager.ShotPaint.WHITE;
import static com.quickactions.actions.screenshot.manager.ShotPaint.font;
import static com.quickactions.actions.screenshot.manager.ShotPaint.guiScale;
import static com.quickactions.actions.screenshot.manager.ShotPaint.lerpColor;
import static com.quickactions.actions.screenshot.manager.ShotPaint.withAlpha;

/** right panel: big preview of the selected shot, its name, date, resolution, size, format */
final class DetailPanel {
    private static final int[] NO_SIZE = new int[0];

    private final ShotModel model;
    private final GalleryLayout layout;
    private final PendingTooltip tooltip;
    private float previewHover;

    DetailPanel(ShotModel model, GalleryLayout layout, PendingTooltip tooltip) {
        this.model = model;
        this.layout = layout;
        this.tooltip = tooltip;
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        int left = layout.detailLeft;
        int right = left + DETAIL_WIDTH;
        int panelBottom = layout.panelBottom;
        g.fill(left - 1, PANEL_TOP - 1, right + 1, panelBottom + 1, Theme.border());
        g.fill(left, PANEL_TOP, right, panelBottom, Theme.panelBg());

        ScreenshotInfo info = model.selectedInfo();
        if (info == null) {
            ShotPaint.centered(g, font(), left, DETAIL_WIDTH, PANEL_TOP + layout.viewportHeight() / 2,
                    Component.translatable("quickactions.shots.detail.empty_title"),
                    Component.translatable("quickactions.shots.detail.empty_hint"));
            return;
        }

        Rect box = drawPreview(g, info, mouseX, mouseY, dt);

        // name and date
        int textLeft = left + 12;
        int textWidth = DETAIL_WIDTH - 24;
        int y = box.y() + box.h() + 12;
        String name = UiText.fitEnd(font(), info.name(), textWidth);
        g.text(font(), Component.literal(name), textLeft, y, WHITE, true);
        if (!name.equals(info.name()) && mouseX >= textLeft && mouseX < textLeft + textWidth
                && mouseY >= y - 2 && mouseY < y + font().lineHeight + 2) {
            tooltip.text = info.name(); // long name is cut, full one on hover
        }
        y += font().lineHeight + 3;
        g.text(font(), ShotText.dateLine(info), textLeft, y, Theme.muted(), true);
        y += font().lineHeight + 8;
        g.fill(left + 10, y, right - 10, y + 1, Theme.border());
        y += 8;

        // "name: value" rows
        y = drawInfoRow(g, left, right, y, "quickactions.shots.detail.resolution", resolutionText(info));
        y = drawInfoRow(g, left, right, y, "quickactions.shots.detail.size", UiText.formatBytes(info.sizeBytes()));
        drawInfoRow(g, left, right, y, "quickactions.shots.detail.format", formatText(info));

        // hint at panel bottom
        List<String> tips = UiText.wrap(font(),
                Component.translatable(model.selectMode
                        ? "quickactions.shots.detail.tips_select" : "quickactions.shots.detail.tips").getString(),
                DETAIL_WIDTH - 24);
        int tipY = panelBottom - 8 - tips.size() * (font().lineHeight + 2);
        for (String line : tips) {
            g.text(font(), Component.literal(line), textLeft, tipY, Theme.tone(0.34f), true);
            tipY += font().lineHeight + 2;
        }
    }

    /** the 16:9 preview box with the hover "View" pill, returns the box */
    private Rect drawPreview(GuiGraphicsExtractor g, ScreenshotInfo info, int mouseX, int mouseY, float dt) {
        Rect box = layout.previewRect();
        boolean overPreview = !model.viewerOpen() && box.contains(mouseX, mouseY);
        previewHover += ((overPreview ? 1f : 0f) - previewHover) * Math.min(1f, dt * 14f);

        g.fill(box.x() - 1, box.y() - 1, box.x() + box.w() + 1, box.y() + box.h() + 1,
                lerpColor(Theme.border(), Theme.accent(), previewHover));
        g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), Theme.deep());

        int scale = guiScale();
        Loaded loaded = ScreenshotTextures.get(info, Mode.FIT, box.w() * scale, box.h() * scale);
        if (loaded != null) {
            int w = Math.min(box.w(), loaded.width() / scale);
            int h = Math.min(box.h(), loaded.height() / scale);
            g.blit(RenderPipelines.GUI_TEXTURED, loaded.id(),
                    box.x() + (box.w() - w) / 2, box.y() + (box.h() - h) / 2, 0, 0, w, h, w, h);
        } else {
            Loaded fallback = ScreenshotTextures.fallback(info);
            if (fallback != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, fallback.id(), box.x(), box.y(), 0, 0,
                        box.w(), box.h(), box.w(), box.h());
            } else if (ScreenshotTextures.failed(info, Mode.FIT, box.w() * scale, box.h() * scale)) {
                ShotPaint.broken(g, font(), box.x(), box.y(), box.w(), box.h());
            } else {
                ShotPaint.shimmer(g, box.x(), box.y(), box.w(), box.h());
            }
        }
        if (previewHover > 0.02f) {
            Component view = Component.translatable("quickactions.shots.detail.view");
            int pillWidth = font().width(view) + 14;
            int pillX = box.x() + (box.w() - pillWidth) / 2;
            int pillY = box.y() + box.h() - 18;
            g.fill(pillX, pillY, pillX + pillWidth, pillY + 13, withAlpha(0x000000, 0.72f * previewHover));
            g.text(font(), view, pillX + 7, pillY + 3, withAlpha(0xFFFFFF, previewHover), true);
        }
        return box;
    }

    private int drawInfoRow(GuiGraphicsExtractor g, int left, int right, int y, String labelKey, String value) {
        Component label = Component.translatable(labelKey);
        g.text(font(), label, left + 12, y, Theme.muted(), true);
        g.text(font(), Component.literal(value), right - 12 - font().width(value), y, WHITE, true);
        return y + font().lineHeight + 7;
    }

    private String resolutionText(ScreenshotInfo info) {
        int[] size = model.sizeCache.computeIfAbsent(info.file(), file -> {
            int[] read = FileOps.readPngSize(file);
            return read != null ? read : NO_SIZE;
        });
        return size.length == 2 ? size[0] + " × " + size[1] : "—";
    }

    private String formatText(ScreenshotInfo info) {
        String name = info.name();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "—";
    }
}
