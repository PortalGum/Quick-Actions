package com.quickactions.actions.screenshot.manager;

import com.quickactions.actions.screenshot.ScreenshotInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * geometry of the screen: where the gallery and detail panel are, how cards are laid out by day,
 * scroll position and hit tests (what's under the mouse)
 * knows nothing about drawing or input
 */
final class GalleryLayout {
    static final int PANEL_TOP = 40;
    static final int FOOTER = 40;
    static final int DETAIL_WIDTH = 218;
    static final int PANEL_GAP = 8;
    static final int GALLERY_MAX_WIDTH = 620;
    static final int PAD = 6;
    static final int CARD_MIN_WIDTH = 104;
    static final int CARD_GAP = 6;
    static final int HEADER_HEIGHT = 20;
    static final int HEADER_LABEL_OFFSET = 24;
    static final int SCROLLBAR_WIDTH = 6;
    static final int WHEEL_STEP = 54;
    static final int MIN_WIDTH_FOR_DETAIL = 440;

    static final int CHECK_RADIUS = 7;
    static final int CHECK_PAD = 5;
    static final int EXPAND_SIZE = 15;
    static final int EXPAND_PAD = 4;

    static final int ZONE_NONE = 0;
    static final int ZONE_CARD = 1;
    static final int ZONE_CHECK = 2;
    static final int ZONE_EXPAND = 3;

    /** screen size, set by {@link #configure} */
    int width;
    int height;

    final List<Cell> cells = new ArrayList<>();
    /** shot index -> index in {@link #cells} */
    int[] shotCell = new int[0];
    int contentHeight;
    int columns = 1;
    int cardWidth;
    int cardHeight;
    int thumbWidth;
    int thumbHeight;

    int galleryLeft;
    int galleryWidth;
    int detailLeft;
    int panelBottom;
    boolean showDetail;

    float scroll;
    float scrollTarget;

    private final ShotModel model;

    GalleryLayout(ShotModel model) {
        this.model = model;
    }

    /** places the panels for the current window size (called on every screen init) */
    void configure(int screenWidth, int screenHeight) {
        width = screenWidth;
        height = screenHeight;
        showDetail = width >= MIN_WIDTH_FOR_DETAIL;
        int detail = showDetail ? DETAIL_WIDTH + PANEL_GAP : 0;
        galleryWidth = Math.min(GALLERY_MAX_WIDTH, width - 24 - detail);
        int group = galleryWidth + detail;
        galleryLeft = width / 2 - group / 2;
        detailLeft = galleryLeft + galleryWidth + PANEL_GAP;
        panelBottom = height - FOOTER;
    }

    int viewportHeight() {
        return panelBottom - PANEL_TOP;
    }

    // ---------- cards by day ----------

    void rebuild(int lineHeight) {
        List<ScreenshotInfo> shots = model.shots;
        cells.clear();
        shotCell = new int[shots.size()];

        int innerWidth = galleryWidth - PAD * 2 - SCROLLBAR_WIDTH;
        columns = Math.max(1, (innerWidth + CARD_GAP) / (CARD_MIN_WIDTH + CARD_GAP));
        cardWidth = (innerWidth + CARD_GAP) / columns - CARD_GAP;
        thumbWidth = cardWidth - 2;
        thumbHeight = thumbWidth * 9 / 16;
        cardHeight = 1 + thumbHeight + lineHeight + 8;

        // screenshots per day
        Map<LocalDate, Integer> perDay = new LinkedHashMap<>();
        for (ScreenshotInfo info : shots) {
            perDay.merge(info.time().toLocalDate(), 1, Integer::sum);
        }

        int y = PAD;
        int column = 0;
        LocalDate group = null;
        for (int i = 0; i < shots.size(); i++) {
            LocalDate day = shots.get(i).time().toLocalDate();
            if (!day.equals(group)) {
                if (group != null) {
                    if (column != 0) {
                        y += cardHeight + CARD_GAP;
                        column = 0;
                    }
                    y += 6;
                }
                cells.add(new Cell(-1, ShotText.dayLabel(day), perDay.get(day), PAD, y, innerWidth, HEADER_HEIGHT));
                y += HEADER_HEIGHT + 4;
                group = day;
            }
            cells.add(new Cell(i, null, 0, PAD + column * (cardWidth + CARD_GAP), y, cardWidth, cardHeight));
            shotCell[i] = cells.size() - 1;
            column++;
            if (column == columns) {
                column = 0;
                y += cardHeight + CARD_GAP;
            }
        }
        if (column != 0) {
            y += cardHeight + CARD_GAP;
        }
        contentHeight = y + PAD - CARD_GAP;
        clampScroll();
    }

    /** first shot of the day that starts at this header cell */
    int firstShotOf(int headerIndex) {
        return cells.get(headerIndex + 1).shot();
    }

    // ---------- scroll position ----------

    void clampScroll() {
        float max = Math.max(0, contentHeight - viewportHeight());
        scrollTarget = Math.max(0, Math.min(scrollTarget, max));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    /** smooth follow of the scroll position */
    void tickScroll(float dt) {
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 18f));
        if (Math.abs(scrollTarget - scroll) < 0.3f) scroll = scrollTarget;
    }

    int scrollOffset() {
        return Math.round(scroll);
    }

    void ensureVisible(int shot) {
        if (shot < 0 || shot >= shotCell.length) return;
        int index = shotCell[shot];
        Cell cell = cells.get(index);
        Cell previous = index > 0 ? cells.get(index - 1) : null;
        int top = (previous != null && previous.isHeader() ? previous.y() : cell.y()) - PAD;
        int bottom = cell.y() + cell.h() + PAD;
        if (top < scrollTarget) {
            scrollTarget = top;
        } else if (bottom > scrollTarget + viewportHeight()) {
            scrollTarget = bottom - viewportHeight();
        }
        clampScroll();
    }

    // ---------- hit tests (screen coords) ----------

    boolean inGallery(double mx, double my) {
        return mx >= galleryLeft && mx < galleryLeft + galleryWidth && my >= PANEL_TOP && my < panelBottom;
    }

    /** x in gallery content coords */
    double contentX(double mx) {
        return mx - galleryLeft;
    }

    /** y in gallery content coords (with scroll) */
    double contentY(double my) {
        return my - PANEL_TOP + Math.round(scroll);
    }

    int shotAt(double mx, double my) {
        if (!inGallery(mx, my)) return -1;
        double cx = contentX(mx);
        double cy = contentY(my);
        for (Cell cell : cells) {
            if (cell.y() > cy) break;
            if (cell.isHeader()) continue;
            if (cx >= cell.x() && cx < cell.x() + cell.w() && cy >= cell.y() && cy < cell.y() + cell.h()) {
                return cell.shot();
            }
        }
        return -1;
    }

    /** index in {@link #cells} of the day header under the mouse, -1 if none */
    int headerAt(double mx, double my) {
        if (!inGallery(mx, my)) return -1;
        double cx = contentX(mx);
        double cy = contentY(my);
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (cell.y() > cy) break;
            if (!cell.isHeader()) continue;
            if (cx >= cell.x() && cx < cell.x() + cell.w() && cy >= cell.y() && cy < cell.y() + cell.h()) return i;
        }
        return -1;
    }

    Rect headerCheckRect(Cell header) {
        int x = galleryLeft + header.x();
        int y = PANEL_TOP + header.y() - Math.round(scroll);
        return new Rect(x + 3, y, HEADER_LABEL_OFFSET - 4, header.h());
    }

    Rect cardRect(int shot) {
        Cell cell = cells.get(shotCell[shot]);
        return new Rect(galleryLeft + cell.x(), PANEL_TOP + cell.y() - Math.round(scroll), cell.w(), cell.h());
    }

    Rect checkRect(Rect card) {
        int size = CHECK_RADIUS * 2 + 1 + 4;
        return new Rect(card.x() + 1 + CHECK_PAD - 2, card.y() + 1 + CHECK_PAD - 2, size, size);
    }

    Rect expandRect(Rect card) {
        return new Rect(card.x() + 1 + thumbWidth - EXPAND_SIZE - EXPAND_PAD,
                card.y() + 1 + thumbHeight - EXPAND_SIZE - EXPAND_PAD, EXPAND_SIZE, EXPAND_SIZE);
    }

    int zoneAt(int shot, double mx, double my) {
        if (shot < 0) return ZONE_NONE;
        Rect card = cardRect(shot);
        if (checkRect(card).contains(mx, my)) return ZONE_CHECK;
        if (model.selectMode && expandRect(card).contains(mx, my)) return ZONE_EXPAND;
        return ZONE_CARD;
    }

    /** big preview in the detail panel */
    Rect previewRect() {
        int w = DETAIL_WIDTH - 20;
        return new Rect(detailLeft + 10, PANEL_TOP + 10, w, w * 9 / 16);
    }
}
