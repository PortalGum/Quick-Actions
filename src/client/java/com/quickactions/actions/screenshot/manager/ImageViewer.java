package com.quickactions.actions.screenshot.manager;

import com.mojang.blaze3d.platform.InputConstants;
import com.quickactions.actions.screenshot.ScreenshotInfo;
import com.quickactions.actions.screenshot.ScreenshotTextures;
import com.quickactions.actions.screenshot.ScreenshotTextures.Loaded;
import com.quickactions.actions.screenshot.ScreenshotTextures.Mode;
import com.quickactions.compat.GuiBlit;
import com.quickactions.ui.Clip;
import com.quickactions.ui.Cursors;
import com.quickactions.ui.Icons;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.UiText;
import com.quickactions.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

import static com.quickactions.actions.screenshot.manager.ShotPaint.WHITE;
import static com.quickactions.actions.screenshot.manager.ShotPaint.clampF;
import static com.quickactions.actions.screenshot.manager.ShotPaint.font;
import static com.quickactions.actions.screenshot.manager.ShotPaint.fillPlate;
import static com.quickactions.actions.screenshot.manager.ShotPaint.guiScale;
import static com.quickactions.actions.screenshot.manager.ShotPaint.withAlpha;
import static com.quickactions.actions.screenshot.manager.ViewerZoom.ZOOM_MAX;
import static com.quickactions.actions.screenshot.manager.ViewerZoom.ZOOM_MIN;
import static com.quickactions.actions.screenshot.manager.ViewerZoom.ZOOM_STEP;

/**
 * fullscreen view of one screenshot: open/close, prev/next, zoom and pan, input and drawing
 * index of the open shot lives in {@link ShotModel#viewer}
 */
final class ImageViewer {
    private static final long VIEWER_FADE_MS = 180;
    private static final int ZOOM_TEXTURE_SIDE = 4096;
    private static final int VIEWER_BACKDROP = 0x05080D;

    private final ShotModel model;
    private final GalleryLayout layout;
    private final IntConsumer revealShot;
    private final Runnable deleteCurrent;
    private final ViewerZoom zoom = new ViewerZoom();

    private long openedMillis;
    private boolean ctrlDown;

    /**
     * @param revealShot selects this shot and scrolls the gallery to it
     * @param deleteCurrent what the delete key does inside the viewer
     */
    ImageViewer(ShotModel model, GalleryLayout layout, IntConsumer revealShot, Runnable deleteCurrent) {
        this.model = model;
        this.layout = layout;
        this.revealShot = revealShot;
        this.deleteCurrent = deleteCurrent;
    }

    boolean isOpen() {
        return model.viewerOpen();
    }

    void open(int shot) {
        if (shot < 0 || shot >= model.size()) return;
        model.viewer = shot;
        openedMillis = System.currentTimeMillis();
        zoom.resetInstant();
        revealShot.accept(shot);
    }

    void close() {
        model.viewer = -1;
        zoom.resetInstant();
    }

    void step(int delta) {
        int next = model.viewer + delta;
        if (next < 0 || next >= model.size()) return;
        model.viewer = next;
        revealShot.accept(next);
    }

    // ---------- input ----------

    private Rect closeRect() {
        return new Rect(layout.width - 34, 10, 22, 22);
    }

    private Rect prevRect() {
        return new Rect(14, layout.height / 2 - 20, 30, 40);
    }

    private Rect nextRect() {
        return new Rect(layout.width - 44, layout.height / 2 - 20, 30, 40);
    }

    /** mouse press while the viewer is open (the screen always eats it) */
    void onClick(double mx, double my, int button) {
        if (button != MouseButtons.LEFT) return;
        if (prevRect().contains(mx, my)) {
            step(-1);
        } else if (nextRect().contains(mx, my)) {
            step(1);
        } else if (closeRect().contains(mx, my) || !zoom.frame.contains(mx, my)) {
            close();
        } else {
            zoom.onImageClick(mx, my);
        }
    }

    void onLeftRelease() {
        zoom.panning = false;
    }

    /** wheel: ctrl zooms, otherwise prev/next */
    void onScroll(double mouseX, double mouseY, double vertical) {
        if (ctrlDown) {
            if (vertical != 0) {
                zoom.zoomAt(zoom.zoomTarget * (float) Math.pow(ZOOM_STEP, vertical), mouseX, mouseY);
            }
            return;
        }
        step(vertical > 0 ? -1 : 1);
    }

    void onKeyPress(int key, int modifiers) {
        ctrlDown = key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
                || (modifiers & PointerGestures.MOD_CONTROL) != 0;
    }

    void onKeyRelease(int key, int modifiers) {
        ctrlDown = key != InputConstants.KEY_LCONTROL && key != InputConstants.KEY_RCONTROL
                && (modifiers & PointerGestures.MOD_CONTROL) != 0;
    }

    /** @return true if the key was used (the screen eats it then) */
    boolean onKey(int key) {
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_UP) {
            step(-1);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_DOWN) {
            step(1);
            return true;
        }
        if (key == InputConstants.KEY_HOME) {
            step(-model.viewer);
            return true;
        }
        if (key == InputConstants.KEY_END) {
            step(model.size() - 1 - model.viewer);
            return true;
        }
        if (key == InputConstants.KEY_DELETE) {
            deleteCurrent.run();
            return true;
        }
        if (key == InputConstants.KEY_0) {
            zoom.panning = false;
            zoom.zoomAtCenter(ZOOM_MIN);
            return true;
        }
        if (key == InputConstants.KEY_EQUALS) {
            zoom.zoomAtCenter(zoom.zoomTarget * ZOOM_STEP);
            return true;
        }
        if (key == InputConstants.KEY_MINUS) {
            zoom.zoomAtCenter(zoom.zoomTarget / ZOOM_STEP);
            return true;
        }
        return false;
    }

    // ---------- drawing ----------

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        int index = model.viewer;
        if (index < 0 || index >= model.size()) return;
        ScreenshotInfo info = model.shots.get(index);
        float open = Math.min(1f, (System.currentTimeMillis() - openedMillis) / (float) VIEWER_FADE_MS);

        g.nextStratum(); // above screen buttons and panels
        g.fill(0, 0, layout.width, layout.height, withAlpha(VIEWER_BACKDROP, 0.94f * open));

        // image area
        int areaLeft = 56;
        int areaTop = 44;
        int areaWidth = layout.width - areaLeft * 2;
        int areaHeight = layout.height - areaTop - 34;
        int scale = guiScale();
        int pxW = areaWidth * scale;
        int pxH = areaHeight * scale;

        Loaded loaded = ScreenshotTextures.get(info, Mode.FIT, pxW, pxH);
        int w;
        int h;
        if (loaded != null) {
            w = Math.min(areaWidth, loaded.width() / scale);
            h = Math.min(areaHeight, loaded.height() / scale);
            prefetchNeighbours(index, pxW, pxH);
        } else {
            w = Math.min(areaWidth, areaHeight * 16 / 9);
            h = w * 9 / 16;
        }
        int x = areaLeft + (areaWidth - w) / 2;
        int y = areaTop + (areaHeight - h) / 2;
        zoom.frame = new Rect(x, y, w, h);
        if (!info.file().equals(zoom.file)) {
            zoom.resetInstant();
            zoom.file = info.file();
        }
        zoom.update(mouseX, mouseY, dt);

        g.fill(x + 4, y + 5, x + w + 4, y + h + 5, withAlpha(0x000000, 0.4f * open));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, withAlpha(Theme.border() & 0xFFFFFF, open));
        g.fill(x, y, x + w, y + h, Theme.deep());
        if (loaded != null) {
            // zoomed in: swap to the full res texture as soon as it's ready
            Loaded sharp = zoom.zoom > 1.001f || zoom.zoomTarget > 1.001f
                    ? ScreenshotTextures.get(info, Mode.FIT, ZOOM_TEXTURE_SIDE, ZOOM_TEXTURE_SIDE) : null;
            drawZoomedImage(g, sharp != null ? sharp : loaded, x, y, w, h);
        } else {
            Loaded fallback = ScreenshotTextures.fallback(info);
            if (fallback != null) {
                GuiBlit.texture(g, fallback.id(), x, y, 0, 0, w, h, w, h);
            } else if (ScreenshotTextures.failed(info, Mode.FIT, pxW, pxH)) {
                ShotPaint.broken(g, font(), x, y, w, h);
            } else {
                ShotPaint.shimmer(g, x, y, w, h);
            }
        }
        if (open < 1f) {
            g.fill(x, y, x + w, y + h, withAlpha(VIEWER_BACKDROP, 1f - open));
        }

        drawTopBar(g, info, index, open, mouseX, mouseY);

        // arrows
        drawArrow(g, prevRect(), true, index > 0, mouseX, mouseY);
        drawArrow(g, nextRect(), false, index < model.size() - 1, mouseX, mouseY);

        // zoom indicator, mini map, cursor
        if (loaded != null) {
            drawZoomHud(g, x, y, w, h);
            drawMiniMap(g, loaded, x, y, w, h);
            if (zoom.isZoomed() && zoom.frame.contains(mouseX, mouseY)) {
                if (zoom.panning) Cursors.move(g);
                else Cursors.hand(g);
            }
        }

        // key hints
        Component hint = zoom.isZoomed()
                ? Component.translatable("quickactions.shots.viewer.hint.zoomed")
                : Component.empty()
                        .append(Component.translatable("quickactions.shots.viewer.hint"))
                        .append(Component.literal("   \u00b7   "))
                        .append(Component.translatable("quickactions.shots.viewer.hint.zoom"));
        g.text(font(), hint, (layout.width - font().width(hint)) / 2, layout.height - 18,
                withAlpha(Theme.tone(0.34f) & 0xFFFFFF, open), true);
    }

    /** name, date, counter and the close button */
    private void drawTopBar(GuiGraphicsExtractor g, ScreenshotInfo info, int index, float open,
                            int mouseX, int mouseY) {
        String name = UiText.fitEnd(font(), info.name(), layout.width - 190);
        g.text(font(), Component.literal(name), 16, 12, withAlpha(0xFFFFFF, open), true);
        g.text(font(), ShotText.dateLine(info), 16, 12 + font().lineHeight + 3,
                withAlpha(Theme.muted() & 0xFFFFFF, open), true);

        Component counter = Component.translatable("quickactions.shots.viewer.counter", index + 1, model.size());
        Rect close = closeRect();
        g.text(font(), counter, close.x() - 12 - font().width(counter), 12 + 2,
                withAlpha(Theme.textSoft() & 0xFFFFFF, open), true);
        drawRoundButton(g, close, close.contains(mouseX, mouseY));
        Icons.cross(g, close.x() + 7, close.y() + 7, 8, WHITE);
    }

    private void drawZoomedImage(GuiGraphicsExtractor g, Loaded tex, int x, int y, int w, int h) {
        int zw = Math.max(w, Math.round(w * zoom.zoom));
        int zh = Math.max(h, Math.round(h * zoom.zoom));
        int left = Math.round(x + (w - zw) / 2f + zoom.panX);
        int top = Math.round(y + (h - zh) / 2f + zoom.panY);
        boolean zoomed = zw != w || zh != h;
        if (zoomed) Clip.begin(g, x, y, x + w, y + h);
        GuiBlit.texture(g, tex.id(), left, top, 0, 0, zw, zh, zw, zh);
        if (zoomed) Clip.end(g);
    }

    private void drawZoomHud(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        float a = zoom.hud;
        int pw = 76;
        int ph = 27;
        if (a < 0.02f || w < pw + 24 || h < ph + 24) return;
        int px = x + (w - pw) / 2;
        int py = y + h - ph - 12;

        fillPlate(g, px - 1, py - 1, pw + 2, ph + 2, withAlpha(Theme.borderStrong() & 0xFFFFFF, a));
        fillPlate(g, px, py, pw, ph, withAlpha(Theme.deep() & 0xFFFFFF, 0.94f * a));

        boolean atMax = zoom.zoom >= ZOOM_MAX - 0.01f;
        String pct = Math.round(zoom.zoom * 100f) + "%";
        int color = atMax ? Theme.accentLight() : Theme.textBright();
        g.text(font(), Component.literal(pct), px + (pw - font().width(pct)) / 2, py + 6,
                withAlpha(color & 0xFFFFFF, a), true);

        // log scale bar: 100% .. 800%
        int bx = px + 9;
        int bw = pw - 18;
        int by = py + ph - 8;
        g.fill(bx, by, bx + bw, by + 2, withAlpha(Theme.tone(0.2f) & 0xFFFFFF, a));
        float t = clampF((float) (Math.log(Math.max(zoom.zoom, 1f)) / Math.log(ZOOM_MAX)), 0f, 1f);
        int filled = Math.round(bw * t);
        if (filled > 0) g.fill(bx, by, bx + filled, by + 2, withAlpha(Theme.accent() & 0xFFFFFF, a));
    }

    private void drawMiniMap(GuiGraphicsExtractor g, Loaded base, int x, int y, int w, int h) {
        float a = zoom.hud;
        if (a < 0.02f || zoom.zoom <= ZOOM_MIN + 0.01f || w < 180 || h < 110) return;
        int mw = Math.max(40, Math.min(72, w / 5));
        int mh = Math.max(24, Math.round(mw * (h / (float) w)));
        int mx = x + w - mw - 14;
        int my = y + h - mh - 14;

        fillPlate(g, mx - 1, my - 1, mw + 2, mh + 2, withAlpha(Theme.borderStrong() & 0xFFFFFF, a));
        GuiBlit.texture(g, base.id(), mx, my, 0, 0, mw, mh, mw, mh);
        g.fill(mx, my, mx + mw, my + mh, withAlpha(0x000000, 0.5f * a));
        g.fill(mx, my, mx + mw, my + mh, withAlpha(VIEWER_BACKDROP, 1f - a)); // fades the thumb itself

        float vw = 1f / zoom.zoom;
        float cx = 0.5f - zoom.panX / (w * zoom.zoom);
        float cy = 0.5f - zoom.panY / (h * zoom.zoom);
        int rx = mx + Math.round((cx - vw / 2f) * mw);
        int ry = my + Math.round((cy - vw / 2f) * mh);
        int rw = Math.max(3, Math.round(vw * mw));
        int rh = Math.max(3, Math.round(vw * mh));
        rx = Math.max(mx, Math.min(mx + mw - rw, rx));
        ry = Math.max(my, Math.min(my + mh - rh, ry));

        int accent = Theme.accent() & 0xFFFFFF;
        g.fill(rx, ry, rx + rw, ry + rh, withAlpha(accent, 0.22f * a));
        int edge = withAlpha(accent, a);
        g.fill(rx, ry, rx + rw, ry + 1, edge);
        g.fill(rx, ry + rh - 1, rx + rw, ry + rh, edge);
        g.fill(rx, ry + 1, rx + 1, ry + rh - 1, edge);
        g.fill(rx + rw - 1, ry + 1, rx + rw, ry + rh - 1, edge);
    }

    /** preloads the neighbours so stepping feels instant */
    private void prefetchNeighbours(int index, int pxW, int pxH) {
        if (index > 0) ScreenshotTextures.get(model.shots.get(index - 1), Mode.FIT, pxW, pxH);
        if (index < model.size() - 1) ScreenshotTextures.get(model.shots.get(index + 1), Mode.FIT, pxW, pxH);
    }

    private void drawRoundButton(GuiGraphicsExtractor g, Rect r, boolean hovered) {
        ShotPaint.roundBox(g, r, hovered ? Theme.accent() : Theme.border(),
                hovered ? Theme.accentDeep() : Theme.card());
    }

    private void drawArrow(GuiGraphicsExtractor g, Rect r, boolean left, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && r.contains(mouseX, mouseY);
        drawRoundButton(g, r, hovered);
        int color = enabled ? WHITE : Theme.scroll();
        Icons.chevron(g, r.x() + (r.w() - Icons.CHEVRON_WIDTH) / 2 + (left ? -1 : 1),
                r.y() + (r.h() - Icons.CHEVRON_HEIGHT) / 2, left, color);
    }
}
