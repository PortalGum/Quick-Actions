package com.quickactions.actions.screenshot.manager;

import com.quickactions.actions.screenshot.ScreenshotInfo;
import com.quickactions.actions.screenshot.ScreenshotTextures;
import com.quickactions.actions.screenshot.ScreenshotTextures.Loaded;
import com.quickactions.actions.screenshot.ScreenshotTextures.Mode;
import com.quickactions.ui.Clip;
import com.quickactions.ui.Icons;
import com.quickactions.ui.UiText;
import com.quickactions.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import static com.quickactions.actions.screenshot.manager.GalleryLayout.CHECK_PAD;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.CHECK_RADIUS;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.EXPAND_PAD;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.EXPAND_SIZE;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.HEADER_LABEL_OFFSET;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.PANEL_TOP;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.SCROLLBAR_WIDTH;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.ZONE_EXPAND;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.ZONE_CHECK;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.ZONE_NONE;
import static com.quickactions.actions.screenshot.manager.ShotPaint.WHITE;
import static com.quickactions.actions.screenshot.manager.ShotPaint.font;
import static com.quickactions.actions.screenshot.manager.ShotPaint.guiScale;
import static com.quickactions.actions.screenshot.manager.ShotPaint.lerpColor;
import static com.quickactions.actions.screenshot.manager.ShotPaint.withAlpha;

/** draws the gallery: day headers, cards with thumbnails, check circles, hover anims, tooltips */
final class GalleryPainter {
    private static final int HEADER_CHECK_RADIUS = 6;
    private static final int CHECKED_INSET = 5;
    private static final long TOOLTIP_DELAY_MS = 550;
    private static final long THUMB_FADE_MS = 240;

    private final ShotModel model;
    private final GalleryLayout layout;
    private final PointerGestures gestures;
    private final ScrollInteraction scrolling;
    private final PendingTooltip tooltip;

    /** per card anim values: hover (0..1) and checked (0..1) */
    private float[] hover = new float[0];
    private float[] pick = new float[0];
    /** fade of the select mode controls, 0..1 */
    private float selectAnim;
    private int hoverShot = -1;
    private int hoverZone = ZONE_NONE;
    private int tooltipShot = -1;
    private long tooltipSince;

    GalleryPainter(ShotModel model, GalleryLayout layout, PointerGestures gestures,
                   ScrollInteraction scrolling, PendingTooltip tooltip) {
        this.model = model;
        this.layout = layout;
        this.gestures = gestures;
        this.scrolling = scrolling;
        this.tooltip = tooltip;
    }

    /** resizes the anim arrays to match the shot list (call after the list changes) */
    void sync(int count) {
        if (hover.length != count) {
            hover = new float[count];
            pick = new float[count];
        }
    }

    void tick(float dt) {
        selectAnim += ((model.selectMode ? 1f : 0f) - selectAnim) * Math.min(1f, dt * 14f);
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        int right = layout.galleryLeft + layout.galleryWidth;
        g.fill(layout.galleryLeft - 1, PANEL_TOP - 1, right + 1, layout.panelBottom + 1, Theme.border());
        g.fill(layout.galleryLeft, PANEL_TOP, right, layout.panelBottom, Theme.panelBg());

        if (model.loading) {
            drawLoading(g);
            return;
        }
        if (model.shots.isEmpty()) {
            ShotPaint.centered(g, font(), layout.galleryLeft, layout.galleryWidth, centerY(),
                    Component.translatable(model.loadFailed
                            ? "quickactions.shots.error.title" : "quickactions.shots.empty.title"),
                    Component.translatable(model.loadFailed
                            ? "quickactions.shots.error.hint" : "quickactions.shots.empty.hint"));
            return;
        }

        int hoveredShot = model.viewerOpen() ? -1 : layout.shotAt(mouseX, mouseY);
        hoverShot = hoveredShot;
        hoverZone = hoveredShot >= 0 ? layout.zoneAt(hoveredShot, mouseX, mouseY) : ZONE_NONE;
        float follow = Math.min(1f, dt * 14f);
        for (int i = 0; i < hover.length; i++) {
            hover[i] += ((i == hoveredShot ? 1f : 0f) - hover[i]) * follow;
            float target = model.checked.isEmpty() ? 0f : (model.checked.contains(model.shots.get(i).file()) ? 1f : 0f);
            pick[i] += (target - pick[i]) * follow;
        }

        int offset = layout.scrollOffset();
        Clip.begin(g, layout.galleryLeft, PANEL_TOP, right, layout.panelBottom);
        for (int ci = 0; ci < layout.cells.size(); ci++) {
            Cell cell = layout.cells.get(ci);
            int y = PANEL_TOP + cell.y() - offset;
            if (y + cell.h() < PANEL_TOP) continue;
            if (y > layout.panelBottom) break;
            int x = layout.galleryLeft + cell.x();
            if (cell.isHeader()) {
                drawDayHeader(g, ci, cell, x, y, mouseX, mouseY);
            } else {
                drawCard(g, cell.shot(), x, y);
            }
        }
        gestures.drawMarquee(g, mouseX, mouseY);
        Clip.end(g);

        scrolling.drawScrollbar(g, right - SCROLLBAR_WIDTH + 1, mouseX, mouseY, dt);
        updateCardTooltip(hoveredShot);
    }

    private int centerY() {
        return PANEL_TOP + layout.viewportHeight() / 2;
    }

    private void drawLoading(GuiGraphicsExtractor g) {
        Component text = Component.translatable("quickactions.shots.loading");
        int centerX = layout.galleryLeft + layout.galleryWidth / 2;
        int centerY = centerY();
        g.text(font(), text, centerX - font().width(text) / 2, centerY - 14, Theme.muted(), true);

        int barWidth = 120;
        int x = centerX - barWidth / 2;
        int y = centerY + 2;
        g.fill(x - 1, y - 1, x + barWidth + 1, y + 5, Theme.border());
        g.fill(x, y, x + barWidth, y + 4, Theme.card());
        float phase = (System.currentTimeMillis() % 1400L) / 1400f;
        int segment = 40;
        int position = (int) ((barWidth + segment) * phase) - segment;
        int start = Math.max(0, position);
        int end = Math.min(barWidth, position + segment);
        if (end > start) g.fill(x + start, y, x + end, y + 4, Theme.accent());
    }

    private void drawDayHeader(GuiGraphicsExtractor g, int headerIndex, Cell cell, int x, int y,
                               int mouseX, int mouseY) {
        g.fill(x, y + 4, x + 2, y + cell.h() - 4, Theme.accent());

        boolean over = !model.viewerOpen() && layout.inGallery(mouseX, mouseY)
                && mouseX >= x && mouseX < x + cell.w() && mouseY >= y && mouseY < y + cell.h();
        float visible = Math.max(selectAnim, over ? 0.9f : 0f);
        if (visible > 0.02f || model.selectMode) {
            int state = model.dayState(cell, layout.firstShotOf(headerIndex));
            int cx = x + 4 + HEADER_CHECK_RADIUS + 2;
            int cy = y + cell.h() / 2;
            boolean overCircle = over && layout.headerCheckRect(cell).contains(mouseX, mouseY);
            Icons.fillCircle(g, cx, cy, HEADER_CHECK_RADIUS, withAlpha(0x000000, 0.35f * visible));
            if (state == 0) {
                Icons.ringCircle(g, cx, cy, HEADER_CHECK_RADIUS, overCircle ? 2 : 1,
                        withAlpha(0xFFFFFF, (overCircle ? 1f : 0.75f) * visible));
            } else {
                Icons.fillCircle(g, cx, cy, HEADER_CHECK_RADIUS, Theme.accent());
                Icons.ringCircle(g, cx, cy, HEADER_CHECK_RADIUS, 1, WHITE);
                if (state == 2) {
                    Icons.checkMark(g, cx, cy, WHITE);
                } else {
                    g.fill(cx - 3, cy, cx + 4, cy + 2, WHITE);
                }
            }
        }
        int textX = x + HEADER_LABEL_OFFSET + 2;

        Component label = cell.label();
        g.text(font(), label, textX, y + (cell.h() - font().lineHeight) / 2, Theme.accentLight(), true);
        String count = String.valueOf(cell.count());
        int labelRight = textX + font().width(label) + 8;
        int pillWidth = font().width(count) + 10;
        g.fill(labelRight, y + 4, labelRight + pillWidth, y + cell.h() - 4, Theme.tile());
        g.text(font(), Component.literal(count), labelRight + 5, y + (cell.h() - font().lineHeight) / 2,
                Theme.muted(), true);
        g.fill(labelRight + pillWidth + 8, y + cell.h() / 2, x + cell.w(), y + cell.h() / 2 + 1, Theme.borderSoft());
    }

    private void drawCard(GuiGraphicsExtractor g, int shot, int x, int y) {
        ScreenshotInfo info = model.shots.get(shot);
        boolean isSelected = shot == model.selected;
        boolean isChecked = !model.checked.isEmpty() && model.checked.contains(info.file());
        float hv = hover[shot];
        float pk = pick[shot];
        float mark = Math.max(pk, isSelected ? 1f : 0f);
        int cardWidth = layout.cardWidth;
        int cardHeight = layout.cardHeight;

        if (isSelected) {
            g.fill(x - 4, y - 4, x + cardWidth + 4, y + cardHeight + 4, Theme.accentAlpha(0x22));
            g.fill(x - 2, y - 2, x + cardWidth + 2, y + cardHeight + 2, Theme.accent());
        } else if (isChecked) {
            g.fill(x - 2, y - 2, x + cardWidth + 2, y + cardHeight + 2, Theme.accentSoft());
        } else {
            g.fill(x - 1, y - 1, x + cardWidth + 1, y + cardHeight + 1, lerpColor(Theme.border(), Theme.accentSoft(), hv));
        }
        g.fill(x, y, x + cardWidth, y + cardHeight,
                lerpColor(lerpColor(Theme.card(), Theme.cardHover(), hv), Theme.accentSelected(), mark));

        int inset = Math.round(pk * CHECKED_INSET);
        int tx = x + 1;
        int ty = y + 1;
        drawThumb(g, info, tx + inset, ty + inset, layout.thumbWidth - inset * 2, layout.thumbHeight - inset * 2);
        if (hv > 0.01f && !isChecked) {
            g.fill(tx, ty, tx + layout.thumbWidth, ty + layout.thumbHeight, withAlpha(0xFFFFFF, hv * 0.10f));
        }

        String caption = info.vanillaName() ? info.time().format(ShotText.TIME_FORMAT) : info.name();
        caption = UiText.fitEnd(font(), caption, cardWidth - 12);
        g.text(font(), Component.literal(caption), x + 6, ty + layout.thumbHeight + 4,
                mark > 0.5f ? WHITE : lerpColor(Theme.muted(), WHITE, hv), true);

        drawCheckCircle(g, shot, tx, ty, hv, pk);
        if (selectAnim > 0.02f) drawExpandButton(g, shot, tx, ty);
    }

    private void drawCheckCircle(GuiGraphicsExtractor g, int shot, int tx, int ty, float hv, float pk) {
        float press = gestures.pressProgress(shot);
        float visible = Math.max(Math.max(selectAnim, pk), Math.max(hv * 0.9f, press));
        if (visible < 0.02f) return;

        int cx = tx + CHECK_PAD + CHECK_RADIUS;
        int cy = ty + CHECK_PAD + CHECK_RADIUS;
        boolean over = shot == hoverShot && hoverZone == ZONE_CHECK;

        Icons.fillCircle(g, cx, cy, CHECK_RADIUS, withAlpha(0x000000, (over ? 0.62f : 0.42f) * visible));
        Icons.ringCircle(g, cx, cy, CHECK_RADIUS, over ? 2 : 1, withAlpha(0xFFFFFF, (over ? 1f : 0.85f) * visible));
        if (press > 0.02f && pk < 0.5f) {
            Icons.fillCircle(g, cx, cy, Math.max(1, Math.round((CHECK_RADIUS - 2) * press)),
                    withAlpha(Theme.accent() & 0xFFFFFF, 0.9f));
        }
        if (pk > 0.02f) {
            Icons.fillCircle(g, cx, cy, CHECK_RADIUS, withAlpha(Theme.accent() & 0xFFFFFF, pk));
            Icons.ringCircle(g, cx, cy, CHECK_RADIUS, 1, withAlpha(0xFFFFFF, pk));
            Icons.checkMark(g, cx, cy, withAlpha(0xFFFFFF, pk));
        }
    }

    private void drawExpandButton(GuiGraphicsExtractor g, int shot, int tx, int ty) {
        int ex = tx + layout.thumbWidth - EXPAND_SIZE - EXPAND_PAD;
        int ey = ty + layout.thumbHeight - EXPAND_SIZE - EXPAND_PAD;
        boolean over = shot == hoverShot && hoverZone == ZONE_EXPAND;
        float a = selectAnim;

        int fill = over ? withAlpha(Theme.accentDeep() & 0xFFFFFF, 0.95f * a) : withAlpha(0x000000, 0.55f * a);
        g.fill(ex + 1, ey, ex + EXPAND_SIZE - 1, ey + EXPAND_SIZE, fill);
        g.fill(ex, ey + 1, ex + EXPAND_SIZE, ey + EXPAND_SIZE - 1, fill);
        Icons.expand(g, ex + (EXPAND_SIZE - 9) / 2, ey + (EXPAND_SIZE - 9) / 2, 9,
                withAlpha(0xFFFFFF, (over ? 1f : 0.88f) * a));
    }

    private void drawThumb(GuiGraphicsExtractor g, ScreenshotInfo info, int x, int y, int w, int h) {
        int scale = guiScale();
        int pxW = ((layout.thumbWidth * scale + 31) / 32) * 32;
        int pxH = Math.max(1, Math.round(pxW * layout.thumbHeight / (float) layout.thumbWidth));

        Loaded loaded = ScreenshotTextures.get(info, Mode.FILL, pxW, pxH);
        if (loaded != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, loaded.id(), x, y, 0, 0, w, h, w, h);
            float fade = 1f - Math.min(1f, (System.currentTimeMillis() - loaded.readyMillis()) / (float) THUMB_FADE_MS);
            if (fade > 0f) {
                g.fill(x, y, x + w, y + h, withAlpha(Theme.tone(0.022f), fade));
            }
        } else if (ScreenshotTextures.failed(info, Mode.FILL, pxW, pxH)) {
            ShotPaint.broken(g, font(), x, y, w, h);
        } else {
            ShotPaint.shimmer(g, x, y, w, h);
        }
    }

    private void updateCardTooltip(int hoveredShot) {
        if (hoveredShot >= 0 && hoverZone == ZONE_EXPAND) {
            tooltip.text = Component.translatable("quickactions.shots.expand.tooltip").getString();
            return;
        }
        long now = System.currentTimeMillis();
        if (hoveredShot != tooltipShot) {
            tooltipShot = hoveredShot;
            tooltipSince = now;
        }
        if (hoveredShot < 0 || hoveredShot >= model.size() || now - tooltipSince < TOOLTIP_DELAY_MS) return;

        ScreenshotInfo info = model.shots.get(hoveredShot);
        tooltip.text = info.name() + "\n" + info.time().toLocalDate() + " " + info.time().format(ShotText.TIME_FORMAT)
                + "  ·  " + UiText.formatBytes(info.sizeBytes());
    }
}
