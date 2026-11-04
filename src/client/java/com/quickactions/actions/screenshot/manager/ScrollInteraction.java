package com.quickactions.actions.screenshot.manager;

import com.quickactions.ui.Icons;
import com.quickactions.ui.theme.Colors;
import com.quickactions.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import static com.quickactions.actions.screenshot.manager.ShotPaint.WHITE;
import static com.quickactions.actions.screenshot.manager.ShotPaint.lerpColor;
import static com.quickactions.actions.screenshot.manager.ShotPaint.withAlpha;

/** scrollbar (thumb drag) and middle click autoscroll, with their drawing */
final class ScrollInteraction {
    private static final int SCROLLBAR_HIT = 10;
    private static final int AUTOSCROLL_DEAD_ZONE = 8;
    private static final float AUTOSCROLL_GAIN = 10f;
    private static final float AUTOSCROLL_EXPONENT = 1.3f;
    private static final float AUTOSCROLL_MAX_SPEED = 5000f;
    private static final long AUTOSCROLL_HOLD_MS = 300;
    private static final int AUTOSCROLL_ANCHOR_RADIUS = 10;

    private final ShotModel model;
    private final GalleryLayout layout;

    // scrollbar thumb drag
    private boolean barDragging;
    private int barGrab;
    private float barHover;

    // middle click autoscroll
    private boolean autoScroll;
    private boolean autoHeld;
    private boolean autoMoved;
    private long autoStartMillis;
    private int autoOriginX;
    private int autoOriginY;

    ScrollInteraction(ShotModel model, GalleryLayout layout) {
        this.model = model;
        this.layout = layout;
    }

    /** screen got (re)initialised, drop half-done interactions */
    void reset() {
        barDragging = false;
        autoScroll = false;
        autoHeld = false;
    }

    boolean isAutoScrolling() {
        return autoScroll;
    }

    // ---------- scrollbar ----------

    /** @return {trackTop, trackHeight, thumbHeight, thumbTop}, null if there's nothing to scroll */
    @Nullable int[] geometry() {
        int viewport = layout.viewportHeight();
        if (model.loading || model.shots.isEmpty() || layout.contentHeight <= viewport) return null;
        int trackTop = GalleryLayout.PANEL_TOP + 3;
        int trackHeight = viewport - 6;
        int thumb = Math.max(18, trackHeight * viewport / layout.contentHeight);
        float max = layout.contentHeight - viewport;
        int thumbTop = trackTop + Math.round((trackHeight - thumb) * (layout.scroll / max));
        return new int[]{trackTop, trackHeight, thumb, thumbTop};
    }

    boolean overScrollbar(double mx, double my) {
        int right = layout.galleryLeft + layout.galleryWidth;
        return mx >= right - SCROLLBAR_HIT && mx < right && my >= GalleryLayout.PANEL_TOP && my < layout.panelBottom
                && geometry() != null;
    }

    void beginBarDrag(double my) {
        int[] geo = geometry();
        if (geo == null) return;
        boolean onThumb = my >= geo[3] && my < geo[3] + geo[2];
        barGrab = onThumb ? (int) (my - geo[3]) : geo[2] / 2;
        barDragging = true;
        updateBarDrag((int) my);
    }

    void updateBarDrag(int mouseY) {
        if (!barDragging) return;
        int[] geo = geometry();
        if (geo == null) {
            barDragging = false;
            return;
        }
        float travel = geo[1] - geo[2];
        if (travel <= 0) return;
        float max = layout.contentHeight - layout.viewportHeight();
        float t = Math.max(0f, Math.min(1f, (mouseY - barGrab - geo[0]) / travel));
        layout.scrollTarget = t * max;
        layout.scroll = layout.scrollTarget;
    }

    void endBarDrag() {
        barDragging = false;
    }

    // ---------- autoscroll (middle mouse button) ----------

    void startAutoScroll(double mx, double my) {
        autoScroll = true;
        autoHeld = true;
        autoMoved = false;
        autoStartMillis = System.currentTimeMillis();
        autoOriginX = (int) mx;
        autoOriginY = (int) my;
    }

    void stopAutoScroll() {
        autoScroll = false;
        autoHeld = false;
        autoMoved = false;
    }

    /** middle button released: short click keeps autoscroll on, a drag or long hold ends it */
    void onMiddleRelease() {
        if (autoHeld) {
            autoHeld = false;
            if (autoMoved || System.currentTimeMillis() - autoStartMillis > AUTOSCROLL_HOLD_MS) {
                stopAutoScroll();
            }
        }
    }

    /** further from anchor = faster scroll */
    void updateAutoScroll(int mouseY, float dt) {
        if (!autoScroll) return;
        if (layout.contentHeight <= layout.viewportHeight()) {
            stopAutoScroll();
            return;
        }
        int dy = mouseY - autoOriginY;
        int dist = Math.abs(dy) - AUTOSCROLL_DEAD_ZONE;
        if (dist <= 0) return;
        autoMoved = true;
        float speed = Math.min(AUTOSCROLL_MAX_SPEED, AUTOSCROLL_GAIN * (float) Math.pow(dist, AUTOSCROLL_EXPONENT));
        layout.scrollTarget += Math.signum(dy) * speed * dt;
        layout.clampScroll();
        layout.scroll = layout.scrollTarget;
    }

    /** every frame: viewer covers the gallery, so no scrollbar drag or autoscroll under it */
    void cancelWhileViewerOpen() {
        barDragging = false;
        stopAutoScroll();
    }

    // ---------- drawing ----------

    void drawScrollbar(GuiGraphicsExtractor g, int x, int mouseX, int mouseY, float dt) {
        int[] geo = geometry();
        if (geo == null) {
            barHover = 0f;
            return;
        }
        boolean hot = barDragging || (!model.viewerOpen() && !autoScroll && overScrollbar(mouseX, mouseY));
        barHover += ((hot ? 1f : 0f) - barHover) * Math.min(1f, dt * 16f);

        int left = x - Math.round(barHover * 2f);
        int right = x + 3;
        g.fill(left, geo[0], right, geo[0] + geo[1], withAlpha(0xFFFFFF, 0.13f + 0.06f * barHover));
        int color = barDragging ? Theme.accent() : lerpColor(Theme.scroll(), Theme.tone(0.48f), barHover);
        g.fill(left, geo[3], right, geo[3] + geo[2], color);
    }

    void drawAutoScrollAnchor(GuiGraphicsExtractor g, int mouseY) {
        int cx = autoOriginX;
        int cy = autoOriginY;
        int dy = mouseY - autoOriginY;
        boolean up = dy < -AUTOSCROLL_DEAD_ZONE;
        boolean down = dy > AUTOSCROLL_DEAD_ZONE;

        Icons.fillCircle(g, cx, cy, AUTOSCROLL_ANCHOR_RADIUS, Colors.withAlpha(Theme.surface(), 0xE0));
        Icons.ringCircle(g, cx, cy, AUTOSCROLL_ANCHOR_RADIUS, 1, up || down ? Theme.accent() : Theme.accentSoft());
        Icons.fillCircle(g, cx, cy, 1, WHITE);
        int idle = Theme.subtle();
        int upColor = up ? WHITE : idle;
        int downColor = down ? WHITE : idle;
        for (int i = 0; i < 4; i++) {
            g.fill(cx - i, cy - 7 + i, cx + i + 1, cy - 6 + i, upColor);
            g.fill(cx - i, cy + 7 - i, cx + i + 1, cy + 8 - i, downColor);
        }
    }
}
