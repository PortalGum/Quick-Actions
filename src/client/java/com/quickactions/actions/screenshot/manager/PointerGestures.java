package com.quickactions.actions.screenshot.manager;

import com.quickactions.actions.screenshot.ScreenshotInfo;
import com.quickactions.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import static com.quickactions.actions.screenshot.manager.ShotPaint.withAlpha;

/**
 * mouse gestures on the gallery: double click, long press (starts select mode)
 * and the drag box that picks lots of cards at once
 */
final class PointerGestures {
    static final long DOUBLE_CLICK_MS = 350;
    static final int MOD_SHIFT = 1;
    static final int MOD_CONTROL = 2;

    private static final long LONG_PRESS_MS = 650;
    private static final long PRESS_FEEDBACK_DELAY_MS = 250;
    private static final int LONG_PRESS_SLOP = 6;
    private static final int DRAG_SLOP = 5;
    private static final int AUTOSCROLL_ZONE = 22;
    private static final float AUTOSCROLL_SPEED = 520f;

    private final ShotModel model;
    private final GalleryLayout layout;

    // double click
    private long lastClickMillis;
    private int lastClickShot = -1;

    // press (long press feedback)
    private int pressShot = -1;
    private long pressMillis;
    private double pressX;
    private double pressY;

    // drag box
    private boolean gestureActive;
    private int gestureShot = -1;
    private boolean gestureDragging;
    private boolean gestureToggleOnRelease;
    private double gestureStartX; // content coords (with scroll)
    private double gestureStartY;
    private double gestureScreenX;
    private double gestureScreenY;
    private final Set<Path> gestureBase = new HashSet<>();

    PointerGestures(ShotModel model, GalleryLayout layout) {
        this.model = model;
        this.layout = layout;
    }

    /** screen got (re)initialised, drop half-done gestures */
    void reset() {
        pressShot = -1;
        gestureActive = false;
        gestureDragging = false;
    }

    /** next click must not count as the second click of a double click */
    void resetClick() {
        lastClickShot = -1;
    }

    /** forget press and click (autoscroll started) */
    void cancelPress() {
        pressShot = -1;
        lastClickShot = -1;
    }

    /**
     * plain left press on a card (not in select mode)
     * @return true if it's the second click of a double click
     */
    boolean press(int shot, double mx, double my) {
        long now = System.currentTimeMillis();
        if (shot == lastClickShot && now - lastClickMillis < DOUBLE_CLICK_MS) {
            lastClickShot = -1;
            return true;
        }
        lastClickShot = shot;
        lastClickMillis = now;
        pressShot = shot;
        pressMillis = now;
        pressX = mx;
        pressY = my;
        return false;
    }

    /** how far along the long press feedback is on this card, 0..1 */
    float pressProgress(int shot) {
        if (shot != pressShot) return 0f;
        long held = System.currentTimeMillis() - pressMillis - PRESS_FEEDBACK_DELAY_MS;
        return Math.max(0f, Math.min(1f, held / (float) (LONG_PRESS_MS - PRESS_FEEDBACK_DELAY_MS)));
    }

    void beginGesture(int shot, double mx, double my, boolean toggleOnRelease) {
        gestureActive = true;
        gestureShot = shot;
        gestureDragging = false;
        gestureToggleOnRelease = toggleOnRelease;
        gestureStartX = layout.contentX(mx);
        gestureStartY = layout.contentY(my);
        gestureScreenX = mx;
        gestureScreenY = my;
        gestureBase.clear();
        gestureBase.addAll(model.checked);
    }

    /** left button released */
    void onLeftRelease() {
        pressShot = -1;
        if (gestureActive) {
            if (!gestureDragging && gestureToggleOnRelease && gestureShot >= 0 && gestureShot < model.size()) {
                model.toggleCheck(gestureShot);
            }
            gestureActive = false;
            gestureDragging = false;
        }
    }

    /** turns a long press into select mode + a drag box */
    void updateLongPress(int mouseX, int mouseY) {
        if (pressShot < 0) return;
        if (model.viewerOpen() || model.selectMode || pressShot >= model.size()
                || Math.abs(mouseX - pressX) > LONG_PRESS_SLOP || Math.abs(mouseY - pressY) > LONG_PRESS_SLOP) {
            pressShot = -1;
            return;
        }
        if (System.currentTimeMillis() - pressMillis >= LONG_PRESS_MS) {
            int shot = pressShot;
            model.toggleCheck(shot);
            lastClickShot = -1;
            pressShot = -1;
            beginGesture(shot, pressX, pressY, false);
        }
    }

    /** drag box follows the mouse and checks the cards inside it */
    void updateGesture(int mouseX, int mouseY, float dt) {
        if (!gestureActive) return;
        if (model.viewerOpen()) {
            gestureActive = false;
            return;
        }
        if (!gestureDragging) {
            if (Math.abs(mouseX - gestureScreenX) <= DRAG_SLOP && Math.abs(mouseY - gestureScreenY) <= DRAG_SLOP) return;
            gestureDragging = true;
            gestureToggleOnRelease = false;
            pressShot = -1;
        }

        if (mouseY < GalleryLayout.PANEL_TOP + AUTOSCROLL_ZONE) {
            layout.scrollTarget -= AUTOSCROLL_SPEED * dt;
        } else if (mouseY > layout.panelBottom - AUTOSCROLL_ZONE) {
            layout.scrollTarget += AUTOSCROLL_SPEED * dt;
        }
        layout.clampScroll();

        double x1 = Math.min(gestureStartX, layout.contentX(mouseX));
        double x2 = Math.max(gestureStartX, layout.contentX(mouseX));
        double y1 = Math.min(gestureStartY, layout.contentY(mouseY));
        double y2 = Math.max(gestureStartY, layout.contentY(mouseY));

        BitSet hit = new BitSet(model.size());
        for (Cell cell : layout.cells) {
            if (cell.y() > y2) break;
            if (cell.isHeader() || cell.y() + cell.h() < y1) continue;
            if (cell.x() + cell.w() >= x1 && cell.x() <= x2) hit.set(cell.shot());
        }

        boolean changed = false;
        for (int i = 0; i < model.size(); i++) {
            ScreenshotInfo info = model.shots.get(i);
            boolean want = hit.get(i) || gestureBase.contains(info.file());
            if (want != model.checked.contains(info.file())) {
                model.setChecked(i, want);
                changed = true;
            }
        }
        if (changed) {
            model.selectMode = true;
            model.selectionChanged();
        }
    }

    void drawMarquee(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!gestureActive || !gestureDragging) return;
        int offset = layout.scrollOffset();
        int x1 = layout.galleryLeft + (int) Math.min(gestureStartX, layout.contentX(mouseX));
        int x2 = layout.galleryLeft + (int) Math.max(gestureStartX, layout.contentX(mouseX));
        int y1 = GalleryLayout.PANEL_TOP - offset + (int) Math.min(gestureStartY, layout.contentY(mouseY) + 0);
        int y2 = GalleryLayout.PANEL_TOP - offset + (int) Math.max(gestureStartY, layout.contentY(mouseY));
        g.fill(x1, y1, x2 + 1, y2 + 1, withAlpha(Theme.accent() & 0xFFFFFF, 0.16f));
        int edge = withAlpha(Theme.accent() & 0xFFFFFF, 0.85f);
        g.fill(x1, y1, x2 + 1, y1 + 1, edge);
        g.fill(x1, y2, x2 + 1, y2 + 1, edge);
        g.fill(x1, y1, x1 + 1, y2 + 1, edge);
        g.fill(x2, y1, x2 + 1, y2 + 1, edge);
    }
}
