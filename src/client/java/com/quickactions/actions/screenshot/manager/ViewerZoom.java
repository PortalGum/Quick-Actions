package com.quickactions.actions.screenshot.manager;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static com.quickactions.actions.screenshot.manager.ShotPaint.clampF;

/** zoom and pan for the fullscreen viewer, 1.0 = image fits the frame (100%) */
final class ViewerZoom {
    static final float ZOOM_MIN = 1f;
    static final float ZOOM_MAX = 8f;
    static final float ZOOM_STEP = 1.22f;
    private static final float ZOOM_DOUBLE_CLICK = 2.5f;
    private static final float ZOOM_SMOOTHING = 16f;
    private static final long ZOOM_HUD_MS = 1600;

    /** where the image is drawn right now (fit rect), the viewer sets it every frame */
    Rect frame = new Rect(0, 0, 0, 0);

    float zoom = 1f;
    float zoomTarget = 1f;
    float panX;
    float panY;
    private float panTargetX;
    private float panTargetY;
    boolean panning;
    private int panLastX;
    private int panLastY;
    /** fade of the percent badge and the mini map, 0..1 */
    float hud;
    private long hudSince;
    private long lastClickMillis;
    /** file the current zoom belongs to, different file = zoom resets */
    @Nullable Path file;

    /** true while the user wants more than 100% */
    boolean isZoomed() {
        return zoomTarget > ZOOM_MIN + 0.001f;
    }

    void resetInstant() {
        zoom = 1f;
        zoomTarget = 1f;
        panX = panY = panTargetX = panTargetY = 0f;
        panning = false;
        hud = 0f;
        hudSince = 0L;
        lastClickMillis = 0L;
        file = null;
    }

    /** zoom to {@code target}, point under (cx, cy) stays where it is */
    void zoomAt(float target, double cx, double cy) {
        Rect f = frame;
        target = clampF(target, ZOOM_MIN, ZOOM_MAX);
        hudSince = System.currentTimeMillis();
        if (Math.abs(target - zoomTarget) < 1e-4f) return;
        double px = clampF((float) cx, f.x(), f.x() + f.w()) - (f.x() + f.w() / 2.0);
        double py = clampF((float) cy, f.y(), f.y() + f.h()) - (f.y() + f.h() / 2.0);
        float ratio = target / zoomTarget;
        panTargetX = (float) (px - (px - panTargetX) * ratio);
        panTargetY = (float) (py - (py - panTargetY) * ratio);
        zoomTarget = target;
        clampTargetPan();
    }

    void zoomAtCenter(float target) {
        Rect f = frame;
        zoomAt(target, f.x() + f.w() / 2.0, f.y() + f.h() / 2.0);
    }

    private void clampTargetPan() {
        Rect f = frame;
        float maxX = f.w() * (zoomTarget - 1f) / 2f;
        float maxY = f.h() * (zoomTarget - 1f) / 2f;
        panTargetX = clampF(panTargetX, -maxX, maxX);
        panTargetY = clampF(panTargetY, -maxY, maxY);
    }

    /** click on the image: double click toggles zoom, single click on a zoomed image starts panning */
    void onImageClick(double mx, double my) {
        long now = System.currentTimeMillis();
        boolean doubleClick = lastClickMillis != 0 && now - lastClickMillis <= PointerGestures.DOUBLE_CLICK_MS;
        lastClickMillis = doubleClick ? 0L : now;
        if (doubleClick) {
            panning = false;
            zoomAt(zoomTarget > ZOOM_MIN + 0.01f ? ZOOM_MIN : ZOOM_DOUBLE_CLICK, mx, my);
            return;
        }
        if (zoomTarget > ZOOM_MIN + 0.001f) {
            panning = true;
            panLastX = (int) Math.floor(mx);
            panLastY = (int) Math.floor(my);
        }
    }

    /** every frame: follow mouse panning, ease zoom/pan toward their targets */
    void update(int mouseX, int mouseY, float dt) {
        Rect f = frame;
        if (panning) {
            float dx = mouseX - panLastX;
            float dy = mouseY - panLastY;
            panLastX = mouseX;
            panLastY = mouseY;
            panTargetX += dx;
            panTargetY += dy;
            panX += dx;
            panY += dy;
        }
        clampTargetPan();

        float k = 1f - (float) Math.exp(-dt * ZOOM_SMOOTHING);
        zoom += (zoomTarget - zoom) * k;
        panX += (panTargetX - panX) * k;
        panY += (panTargetY - panY) * k;
        if (Math.abs(zoomTarget - zoom) < 0.0005f) zoom = zoomTarget;
        if (Math.abs(panTargetX - panX) < 0.05f) panX = panTargetX;
        if (Math.abs(panTargetY - panY) < 0.05f) panY = panTargetY;

        float maxX = f.w() * (zoom - 1f) / 2f;
        float maxY = f.h() * (zoom - 1f) / 2f;
        panX = clampF(panX, -maxX, maxX);
        panY = clampF(panY, -maxY, maxY);

        boolean hudOn = panning || System.currentTimeMillis() - hudSince < ZOOM_HUD_MS;
        hud += ((hudOn ? 1f : 0f) - hud) * Math.min(1f, dt * 12f);
        if (!hudOn && hud < 0.01f) hud = 0f;
    }
}
