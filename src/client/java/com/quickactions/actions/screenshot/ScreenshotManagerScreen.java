package com.quickactions.actions.screenshot;

import com.quickactions.ui.theme.Theme;
import com.quickactions.ui.theme.Colors;

import com.mojang.blaze3d.platform.InputConstants;
import com.quickactions.QuickActionsClient;
import com.quickactions.actions.screenshot.ScreenshotTextures.Loaded;
import com.quickactions.actions.screenshot.ScreenshotTextures.Mode;
import com.quickactions.ui.Clip;
import com.quickactions.ui.ContextMenuHost;
import com.quickactions.ui.Cursors;
import com.quickactions.ui.HelpTip;
import com.quickactions.ui.Icons;
import com.quickactions.ui.MenuItem;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.QuickActionMenu;
import com.quickactions.ui.UiText;
import com.quickactions.util.FileOps;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ScreenshotManagerScreen extends Screen {
    private static final ScreenshotActions ACTIONS = new ScreenshotActions();

    // ---------- layout ----------
    private static final int PANEL_TOP = 40;
    private static final int FOOTER = 40;
    private static final int DETAIL_WIDTH = 218;
    private static final int PANEL_GAP = 8;
    private static final int GALLERY_MAX_WIDTH = 620;
    private static final int PAD = 6;
    private static final int CARD_MIN_WIDTH = 104;
    private static final int CARD_GAP = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int WHEEL_STEP = 54;
    private static final int MIN_WIDTH_FOR_DETAIL = 440;
    private static final long DOUBLE_CLICK_MS = 350;
    private static final long TOOLTIP_DELAY_MS = 550;
    private static final long VIEWER_FADE_MS = 180;
    private static final float ZOOM_MIN = 1f;
    private static final float ZOOM_MAX = 8f;
    private static final float ZOOM_STEP = 1.22f;
    private static final float ZOOM_DOUBLE_CLICK = 2.5f;
    private static final float ZOOM_SMOOTHING = 16f;
    private static final long ZOOM_HUD_MS = 1600;
    private static final int ZOOM_TEXTURE_SIDE = 4096;
    private static final long THUMB_FADE_MS = 240;
    private static final long LONG_PRESS_MS = 650;
    private static final long PRESS_FEEDBACK_DELAY_MS = 250;
    private static final int LONG_PRESS_SLOP = 6;
    private static final int DRAG_SLOP = 5;
    private static final int AUTOSCROLL_ZONE = 22;
    private static final float AUTOSCROLL_SPEED = 520f;
    private static final int HEADER_LABEL_OFFSET = 24;
    private static final int HEADER_CHECK_RADIUS = 6;

    // scrollbar and middle click autoscroll
    private static final int SCROLLBAR_HIT = 10;
    private static final int AUTOSCROLL_DEAD_ZONE = 8;
    private static final float AUTOSCROLL_GAIN = 10f;
    private static final float AUTOSCROLL_EXPONENT = 1.3f;
    private static final float AUTOSCROLL_MAX_SPEED = 5000f;
    private static final long AUTOSCROLL_HOLD_MS = 300;
    private static final int AUTOSCROLL_ANCHOR_RADIUS = 10;

    // select mode
    private static final int MOD_SHIFT = 1;
    private static final int MOD_CONTROL = 2;
    private static final int CHECK_RADIUS = 7;
    private static final int CHECK_PAD = 5;
    private static final int EXPAND_SIZE = 15;
    private static final int EXPAND_PAD = 4;
    private static final int CHECKED_INSET = 5;
    private static final int ZONE_NONE = 0;
    private static final int ZONE_CARD = 1;
    private static final int ZONE_CHECK = 2;
    private static final int ZONE_EXPAND = 3;

    // ---------- colors (same as other screens) ----------
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BROKEN_TILE = 0xFF2A1618;
    private static final int BROKEN_TEXT = 0xFFFF8A8A;
    private static final int VIEWER_BACKDROP = 0x05080D;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private record Cell(int shot, @Nullable Component label, int count, int x, int y, int w, int h) {
        boolean isHeader() {
            return shot < 0;
        }
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final int[] NO_SIZE = new int[0];

    private final Screen parent;

    // data
    private List<ScreenshotInfo> shots = List.of();
    private long totalBytes;
    private boolean started;
    private boolean loading = true;
    private boolean loadFailed;
    private final Map<Path, int[]> sizeCache = new HashMap<>();

    // layout
    private final List<Cell> cells = new ArrayList<>();
    private int[] shotCell = new int[0];
    private float[] hover = new float[0];
    private int contentHeight;
    private int columns = 1;
    private int cardWidth;
    private int cardHeight;
    private int thumbWidth;
    private int thumbHeight;
    private int galleryLeft;
    private int galleryWidth;
    private int detailLeft;
    private int panelBottom;
    private boolean showDetail;

    // state
    private float scroll;
    private float scrollTarget;
    private int selected = -1;
    private int viewer = -1;
    private long viewerOpenedMillis;
    private long lastNanos;
    private long lastClickMillis;
    private int lastClickShot = -1;
    private int tooltipShot = -1;
    private long tooltipSince;
    private float previewHover;
    private @Nullable String pendingTooltip;
    private Rect viewerImage = new Rect(0, 0, 0, 0);

    // viewer zoom: 1.0 = image fitted into the frame (100%)
    private float zoom = 1f;
    private float zoomTarget = 1f;
    private float panX;
    private float panY;
    private float panTargetX;
    private float panTargetY;
    private boolean panning;
    private int panLastX;
    private int panLastY;
    private boolean ctrlDown;
    private float zoomHud;
    private long zoomHudSince;
    private long lastViewerClickMillis;
    private float frameDt;
    private @Nullable Path zoomFile;

    // select mode
    private boolean selectMode;
    private final Set<Path> checked = new LinkedHashSet<>();
    private long checkedBytes;
    private int anchor = -1;
    private float[] pick = new float[0];
    private float selectAnim;
    private int hoverShot = -1;
    private int hoverZone = ZONE_NONE;
    private int pressShot = -1;
    private long pressMillis;
    private double pressX;
    private double pressY;
    private float pillHover;

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

    // drag box gesture
    private boolean gestureActive;
    private int gestureShot = -1;
    private boolean gestureDragging;
    private boolean gestureToggleOnRelease;
    private double gestureStartX; // content coords (with scroll)
    private double gestureStartY;
    private double gestureScreenX;
    private double gestureScreenY;
    private final Set<Path> gestureBase = new HashSet<>();
    private int mouseNowX;
    private int mouseNowY;

    private int barLeft;
    private int barWidth;
    private int barRow;
    private Button folderButton;
    private Button copyButton;
    private Button deleteButton;
    private Button doneButton;

    public ScreenshotManagerScreen(Screen parent) {
        super(Component.translatable("quickactions.shots.title"));
        this.parent = parent;
    }

    // ---------- init ----------

    @Override
    protected void init() {
        showDetail = this.width >= MIN_WIDTH_FOR_DETAIL;
        int detail = showDetail ? DETAIL_WIDTH + PANEL_GAP : 0;
        galleryWidth = Math.min(GALLERY_MAX_WIDTH, this.width - 24 - detail);
        int group = galleryWidth + detail;
        galleryLeft = this.width / 2 - group / 2;
        detailLeft = galleryLeft + galleryWidth + PANEL_GAP;
        panelBottom = this.height - FOOTER;

        barWidth = Math.min(this.width - 24, 500);
        barLeft = this.width / 2 - barWidth / 2;
        barRow = this.height - 28;
        folderButton = addButton("quickactions.shots.button.show_in_folder",
                () -> { if (selectMode) toggleSelectAll(); else showInFolderSelected(); });
        copyButton = addButton("quickactions.shots.button.copy_path", this::copyPathSelected);
        deleteButton = addButton("quickactions.shots.button.delete",
                () -> { if (selectMode) deleteChecked(); else deleteSelected(); });
        doneButton = addButton("quickactions.shots.button.done",
                () -> { if (selectMode) exitSelectMode(); else onClose(); });
        pressShot = -1;
        gestureActive = false;
        gestureDragging = false;
        barDragging = false;
        autoScroll = false;
        autoHeld = false;

        // menu goes first, open menu eats clicks
        ContextMenuHost.install(this, this::menuAt);
        ScreenMouseEvents.allowMouseClick(this).register(this::onClick);
        ScreenMouseEvents.allowMouseRelease(this).register(this::onRelease);
        ScreenMouseEvents.allowMouseScroll(this).register(this::onScroll);
        ScreenKeyboardEvents.allowKeyPress(this).register(this::onKey);
        ScreenKeyboardEvents.allowKeyRelease(this).register(this::onKeyRelease);

        rebuildLayout();
        updateButtons();
        if (!started) {
            started = true;
            startLoading();
        }
    }

    private Button addButton(String key, Runnable action) {
        Button button = Button.builder(Component.translatable(key), b -> action.run())
                .bounds(barLeft, barRow, 60, 20).build();
        addRenderableWidget(button);
        return button;
    }

    private void startLoading() {
        loading = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return ScreenshotLibrary.scan();
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((list, error) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (error != null) {
                QuickActionsClient.LOGGER.error("Failed to read screenshots folder", error);
                loadFailed = true;
                shots = List.of();
            } else {
                shots = list;
            }
            totalBytes = 0;
            for (ScreenshotInfo info : shots) {
                totalBytes += info.sizeBytes();
            }
            rebuildLayout();
            updateButtons();
        }));
    }

    // ---------- layout ----------

    private int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    private int viewportHeight() {
        return panelBottom - PANEL_TOP;
    }

    private void rebuildLayout() {
        cells.clear();
        shotCell = new int[shots.size()];
        if (hover.length != shots.size()) {
            hover = new float[shots.size()];
            pick = new float[shots.size()];
        }

        int innerWidth = galleryWidth - PAD * 2 - SCROLLBAR_WIDTH;
        columns = Math.max(1, (innerWidth + CARD_GAP) / (CARD_MIN_WIDTH + CARD_GAP));
        cardWidth = (innerWidth + CARD_GAP) / columns - CARD_GAP;
        thumbWidth = cardWidth - 2;
        thumbHeight = thumbWidth * 9 / 16;
        cardHeight = 1 + thumbHeight + this.font.lineHeight + 8;

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
                cells.add(new Cell(-1, dayLabel(day), perDay.get(day), PAD, y, innerWidth, HEADER_HEIGHT));
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

    private void clampScroll() {
        float max = Math.max(0, contentHeight - viewportHeight());
        scrollTarget = Math.max(0, Math.min(scrollTarget, max));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private void ensureVisible(int shot) {
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

    private Component dayLabel(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) return Component.translatable("quickactions.shots.today");
        if (day.equals(today.minusDays(1))) return Component.translatable("quickactions.shots.yesterday");
        return fullDate(day);
    }

    private Component fullDate(LocalDate day) {
        return Component.translatable("quickactions.shots.date",
                day.getDayOfMonth(),
                Component.translatable("quickactions.shots.month." + day.getMonthValue()),
                day.getYear());
    }

    // ---------- select and actions ----------

    private @Nullable ScreenshotInfo selectedInfo() {
        return selected >= 0 && selected < shots.size() ? shots.get(selected) : null;
    }

    private void select(int shot, boolean scrollTo) {
        selected = shot;
        if (scrollTo) ensureVisible(shot);
        updateButtons();
    }

    private void updateButtons() {
        if (folderButton == null) return;
        boolean has = selectedInfo() != null;

        if (selectMode) {
            int third = (barWidth - 8) / 3;
            place(folderButton, 0, third);
            place(deleteButton, 1, third);
            place(doneButton, 2, third);
            copyButton.visible = false;
            copyButton.active = false;

            boolean all = !shots.isEmpty() && checked.size() == shots.size();
            folderButton.setMessage(Component.translatable(all
                    ? "quickactions.shots.button.deselect_all" : "quickactions.shots.button.select_all"));
            folderButton.active = !shots.isEmpty();
            deleteButton.setMessage(checked.isEmpty()
                    ? Component.translatable("quickactions.shots.button.delete")
                    : Component.translatable("quickactions.shots.button.delete_count", checked.size()));
            deleteButton.active = !checked.isEmpty();
            doneButton.setMessage(Component.translatable("quickactions.shots.button.cancel_select"));
            doneButton.active = true;
        } else {
            int quarter = (barWidth - 12) / 4;
            place(folderButton, 0, quarter);
            place(copyButton, 1, quarter);
            place(deleteButton, 2, quarter);
            place(doneButton, 3, quarter);
            copyButton.visible = true;

            folderButton.setMessage(Component.translatable("quickactions.shots.button.show_in_folder"));
            deleteButton.setMessage(Component.translatable("quickactions.shots.button.delete"));
            doneButton.setMessage(Component.translatable("quickactions.shots.button.done"));
            folderButton.active = has;
            copyButton.active = has;
            deleteButton.active = has;
            doneButton.active = true;
        }
    }

    private void place(Button button, int index, int width) {
        button.setX(barLeft + index * (width + 4));
        button.setY(barRow);
        button.setWidth(width);
        button.visible = true;
    }

    // ---------- select mode ----------

    private void setChecked(int shot, boolean on) {
        ScreenshotInfo info = shots.get(shot);
        if (on ? checked.add(info.file()) : checked.remove(info.file())) {
            checkedBytes += on ? info.sizeBytes() : -info.sizeBytes();
        }
    }

    private void toggleCheck(int shot) {
        if (shot < 0 || shot >= shots.size()) return;
        boolean on = !checked.contains(shots.get(shot).file());
        setChecked(shot, on);
        anchor = shot;
        if (on) {
            selectMode = true;
        } else if (checked.isEmpty()) {
            selectMode = false;
        }
        updateButtons();
    }

    private void checkRange(int shot) {
        int from = anchor >= 0 && anchor < shots.size() ? anchor : shot;
        for (int i = Math.min(from, shot); i <= Math.max(from, shot); i++) {
            setChecked(i, true);
        }
        selectMode = true;
        anchor = shot;
        updateButtons();
    }

    private void toggleSelectAll() {
        if (shots.isEmpty()) return;
        boolean all = checked.size() == shots.size();
        for (int i = 0; i < shots.size(); i++) {
            setChecked(i, !all);
        }
        selectMode = true;
        updateButtons();
    }

    private void toggleSelectMode() {
        if (selectMode) {
            exitSelectMode();
        } else {
            selectMode = true;
            updateButtons();
        }
    }

    private void exitSelectMode() {
        checked.clear();
        checkedBytes = 0;
        anchor = -1;
        selectMode = false;
        updateButtons();
    }

    private void recomputeChecked() {
        checkedBytes = 0;
        for (ScreenshotInfo info : shots) {
            if (checked.contains(info.file())) checkedBytes += info.sizeBytes();
        }
    }

    private List<ScreenshotInfo> checkedInfos() {
        List<ScreenshotInfo> list = new ArrayList<>(checked.size());
        for (ScreenshotInfo info : shots) {
            if (checked.contains(info.file())) list.add(info);
        }
        return list;
    }

    private void deleteChecked() {
        List<ScreenshotInfo> list = checkedInfos();
        if (list.isEmpty()) return;
        if (list.size() == 1) {
            ScreenshotOperations.delete(targetFor(list.get(0)));
        } else {
            ScreenshotOperations.deleteMany(this, list, this::removeShots);
        }
    }

    private MenuItem bulkMenu() {
        return MenuItem.submenu(Component.translatable("quickactions.menu.title"), List.of(
                MenuItem.action(Component.translatable("quickactions.shots.bulk.delete", checked.size()), true,
                        this::deleteChecked),
                MenuItem.action(Component.translatable("quickactions.shots.bulk.deselect"), true,
                        this::exitSelectMode)));
    }

    // ---------- select by day ----------

    private int headerAt(double mx, double my) {
        if (!inGallery(mx, my)) return -1;
        double cx = mx - galleryLeft;
        double cy = my - PANEL_TOP + Math.round(scroll);
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (cell.y() > cy) break;
            if (!cell.isHeader()) continue;
            if (cx >= cell.x() && cx < cell.x() + cell.w() && cy >= cell.y() && cy < cell.y() + cell.h()) return i;
        }
        return -1;
    }

    private Rect headerCheckRect(Cell header) {
        int x = galleryLeft + header.x();
        int y = PANEL_TOP + header.y() - Math.round(scroll);
        return new Rect(x + 3, y, HEADER_LABEL_OFFSET - 4, header.h());
    }

    private int dayState(int headerIndex) {
        Cell header = cells.get(headerIndex);
        int first = cells.get(headerIndex + 1).shot();
        int picked = 0;
        for (int i = first; i < first + header.count() && i < shots.size(); i++) {
            if (checked.contains(shots.get(i).file())) picked++;
        }
        return picked == 0 ? 0 : picked >= header.count() ? 2 : 1;
    }

    private void toggleDay(int headerIndex) {
        Cell header = cells.get(headerIndex);
        int first = cells.get(headerIndex + 1).shot();
        boolean select = dayState(headerIndex) != 2;
        for (int i = first; i < first + header.count() && i < shots.size(); i++) {
            setChecked(i, select);
        }
        anchor = first;
        if (select) {
            selectMode = true;
        } else if (checked.isEmpty()) {
            selectMode = false;
        }
        updateButtons();
    }

    // ---------- drag box gesture ----------

    private double contentX(double mx) {
        return mx - galleryLeft;
    }

    private double contentY(double my) {
        return my - PANEL_TOP + Math.round(scroll);
    }

    private void beginGesture(int shot, double mx, double my, boolean toggleOnRelease) {
        gestureActive = true;
        gestureShot = shot;
        gestureDragging = false;
        gestureToggleOnRelease = toggleOnRelease;
        gestureStartX = contentX(mx);
        gestureStartY = contentY(my);
        gestureScreenX = mx;
        gestureScreenY = my;
        gestureBase.clear();
        gestureBase.addAll(checked);
    }

    private void updateGesture(int mouseX, int mouseY, float dt) {
        if (!gestureActive) return;
        if (viewer >= 0) {
            gestureActive = false;
            return;
        }
        if (!gestureDragging) {
            if (Math.abs(mouseX - gestureScreenX) <= DRAG_SLOP && Math.abs(mouseY - gestureScreenY) <= DRAG_SLOP) return;
            gestureDragging = true;
            gestureToggleOnRelease = false;
            pressShot = -1;
        }

        if (mouseY < PANEL_TOP + AUTOSCROLL_ZONE) {
            scrollTarget -= AUTOSCROLL_SPEED * dt;
        } else if (mouseY > panelBottom - AUTOSCROLL_ZONE) {
            scrollTarget += AUTOSCROLL_SPEED * dt;
        }
        clampScroll();

        double x1 = Math.min(gestureStartX, contentX(mouseX));
        double x2 = Math.max(gestureStartX, contentX(mouseX));
        double y1 = Math.min(gestureStartY, contentY(mouseY));
        double y2 = Math.max(gestureStartY, contentY(mouseY));

        BitSet hit = new BitSet(shots.size());
        for (Cell cell : cells) {
            if (cell.y() > y2) break;
            if (cell.isHeader() || cell.y() + cell.h() < y1) continue;
            if (cell.x() + cell.w() >= x1 && cell.x() <= x2) hit.set(cell.shot());
        }

        boolean changed = false;
        for (int i = 0; i < shots.size(); i++) {
            boolean want = hit.get(i) || gestureBase.contains(shots.get(i).file());
            if (want != checked.contains(shots.get(i).file())) {
                setChecked(i, want);
                changed = true;
            }
        }
        if (changed) {
            selectMode = true;
            updateButtons();
        }
    }

    private void drawMarquee(GuiGraphicsExtractor g) {
        if (!gestureActive || !gestureDragging) return;
        int offset = Math.round(scroll);
        int x1 = galleryLeft + (int) Math.min(gestureStartX, contentX(mouseNowX));
        int x2 = galleryLeft + (int) Math.max(gestureStartX, contentX(mouseNowX));
        int y1 = PANEL_TOP - offset + (int) Math.min(gestureStartY, contentY(mouseNowY) + 0);
        int y2 = PANEL_TOP - offset + (int) Math.max(gestureStartY, contentY(mouseNowY));
        g.fill(x1, y1, x2 + 1, y2 + 1, withAlpha(Theme.accent() & 0xFFFFFF, 0.16f));
        int edge = withAlpha(Theme.accent() & 0xFFFFFF, 0.85f);
        g.fill(x1, y1, x2 + 1, y1 + 1, edge);
        g.fill(x1, y2, x2 + 1, y2 + 1, edge);
        g.fill(x1, y1, x1 + 1, y2 + 1, edge);
        g.fill(x2, y1, x2 + 1, y2 + 1, edge);
    }

    // ---------- scrollbar ----------

    private int[] scrollbarGeometry() {
        int viewport = viewportHeight();
        if (loading || shots.isEmpty() || contentHeight <= viewport) return null;
        int trackTop = PANEL_TOP + 3;
        int trackHeight = viewport - 6;
        int thumb = Math.max(18, trackHeight * viewport / contentHeight);
        float max = contentHeight - viewport;
        int thumbTop = trackTop + Math.round((trackHeight - thumb) * (scroll / max));
        return new int[]{trackTop, trackHeight, thumb, thumbTop};
    }

    private boolean overScrollbar(double mx, double my) {
        int right = galleryLeft + galleryWidth;
        return mx >= right - SCROLLBAR_HIT && mx < right && my >= PANEL_TOP && my < panelBottom
                && scrollbarGeometry() != null;
    }

    private void beginBarDrag(double my) {
        int[] geo = scrollbarGeometry();
        if (geo == null) return;
        boolean onThumb = my >= geo[3] && my < geo[3] + geo[2];
        barGrab = onThumb ? (int) (my - geo[3]) : geo[2] / 2;
        barDragging = true;
        updateBarDrag((int) my);
    }

    private void updateBarDrag(int mouseY) {
        if (!barDragging) return;
        int[] geo = scrollbarGeometry();
        if (geo == null) {
            barDragging = false;
            return;
        }
        float travel = geo[1] - geo[2];
        if (travel <= 0) return;
        float max = contentHeight - viewportHeight();
        float t = Math.max(0f, Math.min(1f, (mouseY - barGrab - geo[0]) / travel));
        scrollTarget = t * max;
        scroll = scrollTarget;
    }

    // ---------- autoscroll (mouse wheel) ----------

    private void startAutoScroll(double mx, double my) {
        autoScroll = true;
        autoHeld = true;
        autoMoved = false;
        autoStartMillis = System.currentTimeMillis();
        autoOriginX = (int) mx;
        autoOriginY = (int) my;
        pressShot = -1;
        lastClickShot = -1;
    }

    private void stopAutoScroll() {
        autoScroll = false;
        autoHeld = false;
        autoMoved = false;
    }

    /** further from anchor = faster scroll */
    private void updateAutoScroll(int mouseY, float dt) {
        if (!autoScroll) return;
        if (contentHeight <= viewportHeight()) {
            stopAutoScroll();
            return;
        }
        int dy = mouseY - autoOriginY;
        int dist = Math.abs(dy) - AUTOSCROLL_DEAD_ZONE;
        if (dist <= 0) return;
        autoMoved = true;
        float speed = Math.min(AUTOSCROLL_MAX_SPEED, AUTOSCROLL_GAIN * (float) Math.pow(dist, AUTOSCROLL_EXPONENT));
        scrollTarget += Math.signum(dy) * speed * dt;
        clampScroll();
        scroll = scrollTarget;
    }

    private void drawAutoScrollAnchor(GuiGraphicsExtractor g) {
        int cx = autoOriginX;
        int cy = autoOriginY;
        int dy = mouseNowY - autoOriginY;
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

    // ---------- single screenshot actions ----------

    private ScreenshotTarget targetFor(ScreenshotInfo info) {
        return new ScreenshotTarget(info, this, () -> removeShots(List.of(info)), newFile -> replaceShot(info, newFile));
    }

    private void showInFolderSelected() {
        ScreenshotInfo info = selectedInfo();
        if (info != null) ScreenshotOperations.showInFolder(targetFor(info));
    }

    private void copyPathSelected() {
        ScreenshotInfo info = selectedInfo();
        if (info != null) ScreenshotOperations.copyPath(targetFor(info));
    }

    private void deleteSelected() {
        ScreenshotInfo info = viewer >= 0 && viewer < shots.size() ? shots.get(viewer) : selectedInfo();
        if (info != null) ScreenshotOperations.delete(targetFor(info));
    }

    private void removeShots(Collection<ScreenshotInfo> gone) {
        Set<Path> goneFiles = new HashSet<>();
        for (ScreenshotInfo info : gone) {
            goneFiles.add(info.file());
        }

        int oldSelected = selected;
        int remainingBefore = 0;
        List<ScreenshotInfo> remaining = new ArrayList<>(shots.size());
        for (int i = 0; i < shots.size(); i++) {
            ScreenshotInfo info = shots.get(i);
            if (goneFiles.contains(info.file())) {
                ScreenshotTextures.forget(info);
                sizeCache.remove(info.file());
                totalBytes -= info.sizeBytes();
                checked.remove(info.file());
            } else {
                if (i < oldSelected) remainingBefore++;
                remaining.add(info);
            }
        }
        shots = remaining;

        if (shots.isEmpty()) {
            selected = -1;
            viewer = -1;
        } else {
            selected = oldSelected < 0 ? -1 : Math.min(remainingBefore, shots.size() - 1);
            if (viewer >= 0) viewer = selected;
        }
        anchor = -1;
        if (checked.isEmpty()) selectMode = false;
        recomputeChecked();
        rebuildLayout();
        ensureVisible(selected);
        updateButtons();
    }

    private void replaceShot(ScreenshotInfo old, Path newFile) {
        ScreenshotInfo fresh;
        try {
            ScreenshotInfo read = ScreenshotLibrary.read(newFile);
            fresh = read.vanillaName() ? read
                    : new ScreenshotInfo(newFile, read.name(), read.sizeBytes(), old.time(), false);
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to read renamed screenshot {}", newFile, e);
            startLoading();
            return;
        }

        Path selectedFile = selected >= 0 && selected < shots.size() ? shots.get(selected).file() : null;
        Path viewerFile = viewer >= 0 && viewer < shots.size() ? shots.get(viewer).file() : null;
        if (old.file().equals(selectedFile)) selectedFile = fresh.file();
        if (old.file().equals(viewerFile)) viewerFile = fresh.file();

        ScreenshotTextures.forget(old);
        sizeCache.remove(old.file());
        if (checked.remove(old.file())) checked.add(fresh.file());

        List<ScreenshotInfo> copy = new ArrayList<>(shots.size());
        for (ScreenshotInfo info : shots) {
            copy.add(info.file().equals(old.file()) ? fresh : info);
        }
        copy.sort(ScreenshotLibrary.NEWEST_FIRST);
        shots = copy;

        selected = indexOfFile(selectedFile);
        viewer = viewerFile == null ? -1 : indexOfFile(viewerFile);
        anchor = -1;
        rebuildLayout();
        ensureVisible(selected);
        updateButtons();
    }

    private int indexOfFile(@Nullable Path file) {
        if (file == null) return -1;
        for (int i = 0; i < shots.size(); i++) {
            if (shots.get(i).file().equals(file)) return i;
        }
        return -1;
    }

    private @Nullable List<MenuItem> menuAt(double x, double y) {
        stopAutoScroll();
        if (!ACTIONS.category().isEnabled()) return null;

        int shot = -1;
        if (viewer >= 0) {
            shot = viewer;
        } else if (inGallery(x, y)) {
            shot = shotAt(x, y);
            if (shot >= 0) {
                boolean inSelection = selectMode && checked.size() > 1 && checked.contains(shots.get(shot).file());
                select(shot, false);
                if (inSelection) return List.of(bulkMenu());
            }
        } else if (showDetail && previewRect().contains(x, y)) {
            shot = selected;
        }
        if (shot < 0 || shot >= shots.size()) return null;
        return List.of(QuickActionMenu.build(ACTIONS, targetFor(shots.get(shot))));
    }

    // ---------- fullscreen view ----------

    private void openViewer(int shot) {
        if (shot < 0 || shot >= shots.size()) return;
        viewer = shot;
        viewerOpenedMillis = System.currentTimeMillis();
        resetZoomInstant();
        select(shot, true);
    }

    private void closeViewer() {
        viewer = -1;
        resetZoomInstant();
    }

    // ---------- viewer zoom ----------

    private void resetZoomInstant() {
        zoom = 1f;
        zoomTarget = 1f;
        panX = panY = panTargetX = panTargetY = 0f;
        panning = false;
        zoomHud = 0f;
        zoomHudSince = 0L;
        lastViewerClickMillis = 0L;
        zoomFile = null;
    }

    private static float clampF(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** zoom to {@code target}, keeping the point under (cx, cy) in place */
    private void zoomAt(float target, double cx, double cy) {
        Rect f = viewerImage;
        target = clampF(target, ZOOM_MIN, ZOOM_MAX);
        zoomHudSince = System.currentTimeMillis();
        if (Math.abs(target - zoomTarget) < 1e-4f) return;
        double px = clampF((float) cx, f.x(), f.x() + f.w()) - (f.x() + f.w() / 2.0);
        double py = clampF((float) cy, f.y(), f.y() + f.h()) - (f.y() + f.h() / 2.0);
        float ratio = target / zoomTarget;
        panTargetX = (float) (px - (px - panTargetX) * ratio);
        panTargetY = (float) (py - (py - panTargetY) * ratio);
        zoomTarget = target;
        clampTargetPan();
    }

    private void zoomAtCenter(float target) {
        Rect f = viewerImage;
        zoomAt(target, f.x() + f.w() / 2.0, f.y() + f.h() / 2.0);
    }

    private void clampTargetPan() {
        Rect f = viewerImage;
        float maxX = f.w() * (zoomTarget - 1f) / 2f;
        float maxY = f.h() * (zoomTarget - 1f) / 2f;
        panTargetX = clampF(panTargetX, -maxX, maxX);
        panTargetY = clampF(panTargetY, -maxY, maxY);
    }

    private void updateZoom(int mouseX, int mouseY) {
        Rect f = viewerImage;
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

        float k = 1f - (float) Math.exp(-frameDt * ZOOM_SMOOTHING);
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

        boolean hudOn = panning || System.currentTimeMillis() - zoomHudSince < ZOOM_HUD_MS;
        zoomHud += ((hudOn ? 1f : 0f) - zoomHud) * Math.min(1f, frameDt * 12f);
        if (!hudOn && zoomHud < 0.01f) zoomHud = 0f;
    }

    private void stepViewer(int delta) {
        int next = viewer + delta;
        if (next < 0 || next >= shots.size()) return;
        viewer = next;
        select(next, true);
    }

    private Rect closeRect() {
        return new Rect(this.width - 34, 10, 22, 22);
    }

    private Rect prevRect() {
        return new Rect(14, this.height / 2 - 20, 30, 40);
    }

    private Rect nextRect() {
        return new Rect(this.width - 44, this.height / 2 - 20, 30, 40);
    }

    // ---------- input ----------

    private boolean inGallery(double mx, double my) {
        return mx >= galleryLeft && mx < galleryLeft + galleryWidth && my >= PANEL_TOP && my < panelBottom;
    }

    private int shotAt(double mx, double my) {
        if (!inGallery(mx, my)) return -1;
        double cx = mx - galleryLeft;
        double cy = my - PANEL_TOP + Math.round(scroll);
        for (Cell cell : cells) {
            if (cell.y() > cy) break;
            if (cell.isHeader()) continue;
            if (cx >= cell.x() && cx < cell.x() + cell.w() && cy >= cell.y() && cy < cell.y() + cell.h()) {
                return cell.shot();
            }
        }
        return -1;
    }

    private Rect previewRect() {
        int w = DETAIL_WIDTH - 20;
        return new Rect(detailLeft + 10, PANEL_TOP + 10, w, w * 9 / 16);
    }

    // ---------- card geometry (screen coords) ----------

    private Rect cardRect(int shot) {
        Cell cell = cells.get(shotCell[shot]);
        return new Rect(galleryLeft + cell.x(), PANEL_TOP + cell.y() - Math.round(scroll), cell.w(), cell.h());
    }

    private Rect checkRect(Rect card) {
        int size = CHECK_RADIUS * 2 + 1 + 4;
        return new Rect(card.x() + 1 + CHECK_PAD - 2, card.y() + 1 + CHECK_PAD - 2, size, size);
    }

    private Rect expandRect(Rect card) {
        return new Rect(card.x() + 1 + thumbWidth - EXPAND_SIZE - EXPAND_PAD,
                card.y() + 1 + thumbHeight - EXPAND_SIZE - EXPAND_PAD, EXPAND_SIZE, EXPAND_SIZE);
    }

    private int zoneAt(int shot, double mx, double my) {
        if (shot < 0) return ZONE_NONE;
        Rect card = cardRect(shot);
        if (checkRect(card).contains(mx, my)) return ZONE_CHECK;
        if (selectMode && expandRect(card).contains(mx, my)) return ZONE_EXPAND;
        return ZONE_CARD;
    }

    private Rect selectPillRect() {
        Component label = Component.translatable(selectMode
                ? "quickactions.shots.select.cancel" : "quickactions.shots.select");
        return new Rect(galleryLeft, 10, 16 + this.font.width(label) + 8, 17);
    }

    private boolean pillVisible() {
        if (loading || shots.isEmpty()) return false;
        int titleLeft = (this.width - (Icons.CAMERA_WIDTH + 7 + this.font.width(this.title))) / 2;
        Rect pill = selectPillRect();
        return pill.x() + pill.w() + 8 < titleLeft;
    }

    // ---------- mouse ----------

    /** @return false to eat the click */
    private boolean onClick(Screen screen, MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        boolean ctrl = (event.modifiers() & MOD_CONTROL) != 0;
        boolean shift = (event.modifiers() & MOD_SHIFT) != 0;

        if (autoScroll) {
            stopAutoScroll();
            return false;
        }

        if (viewer >= 0) {
            if (button == MouseButtons.LEFT) {
                if (prevRect().contains(mx, my)) {
                    stepViewer(-1);
                } else if (nextRect().contains(mx, my)) {
                    stepViewer(1);
                } else if (closeRect().contains(mx, my) || !viewerImage.contains(mx, my)) {
                    closeViewer();
                } else {
                    viewerImageClick(mx, my);
                }
            }
            return false;
        }

        if (button == MouseButtons.MIDDLE) {
            if (inGallery(mx, my) && scrollbarGeometry() != null) {
                startAutoScroll(mx, my);
                return false;
            }
            return true;
        }

        if (button != MouseButtons.LEFT) return true; // rmb already handled by ContextMenuHost

        if (overScrollbar(mx, my)) {
            beginBarDrag(my);
            lastClickShot = -1;
            return false;
        }

        if (pillVisible() && selectPillRect().contains(mx, my)) {
            toggleSelectMode();
            return false;
        }

        int header = headerAt(mx, my);
        if (header >= 0) {
            if (selectMode || headerCheckRect(cells.get(header)).contains(mx, my)) {
                toggleDay(header);
                lastClickShot = -1;
                return false;
            }
            return true;
        }

        int shot = shotAt(mx, my);
        if (shot >= 0) {
            int zone = zoneAt(shot, mx, my);
            if (zone == ZONE_CHECK && !selectMode) {
                toggleCheck(shot);
                select(shot, false);
                lastClickShot = -1;
            } else if (zone == ZONE_EXPAND) {
                select(shot, false);
                openViewer(shot);
            } else if (ctrl) {
                toggleCheck(shot);
                select(shot, false);
                lastClickShot = -1;
            } else if (shift && (selectMode || selected >= 0)) {
                if (!selectMode && anchor < 0) anchor = selected;
                checkRange(shot);
                select(shot, false);
                lastClickShot = -1;
            } else if (selectMode) {
                select(shot, false);
                beginGesture(shot, mx, my, true);
                lastClickShot = -1;
            } else {
                select(shot, false);
                long now = System.currentTimeMillis();
                if (shot == lastClickShot && now - lastClickMillis < DOUBLE_CLICK_MS) {
                    lastClickShot = -1;
                    openViewer(shot);
                } else {
                    lastClickShot = shot;
                    lastClickMillis = now;
                    pressShot = shot;
                    pressMillis = now;
                    pressX = mx;
                    pressY = my;
                }
            }
            return false;
        }
        if (showDetail && selectedInfo() != null && previewRect().contains(mx, my)) {
            openViewer(selected);
            return false;
        }
        return true;
    }

    private boolean onRelease(Screen screen, MouseButtonEvent event) {
        if (event.button() == MouseButtons.LEFT) panning = false;
        if (event.button() == MouseButtons.MIDDLE) {
            if (autoHeld) {
                autoHeld = false;
                if (autoMoved || System.currentTimeMillis() - autoStartMillis > AUTOSCROLL_HOLD_MS) {
                    stopAutoScroll();
                }
            }
            return true;
        }
        if (event.button() != MouseButtons.LEFT) return true;
        barDragging = false;
        pressShot = -1;
        if (gestureActive) {
            if (!gestureDragging && gestureToggleOnRelease && gestureShot >= 0 && gestureShot < shots.size()) {
                toggleCheck(gestureShot);
            }
            gestureActive = false;
            gestureDragging = false;
        }
        return true;
    }

    private void viewerImageClick(double mx, double my) {
        long now = System.currentTimeMillis();
        boolean doubleClick = lastViewerClickMillis != 0 && now - lastViewerClickMillis <= DOUBLE_CLICK_MS;
        lastViewerClickMillis = doubleClick ? 0L : now;
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

    private void updateLongPress(int mouseX, int mouseY) {
        if (pressShot < 0) return;
        if (viewer >= 0 || selectMode || pressShot >= shots.size()
                || Math.abs(mouseX - pressX) > LONG_PRESS_SLOP || Math.abs(mouseY - pressY) > LONG_PRESS_SLOP) {
            pressShot = -1;
            return;
        }
        if (System.currentTimeMillis() - pressMillis >= LONG_PRESS_MS) {
            int shot = pressShot;
            toggleCheck(shot);
            lastClickShot = -1;
            pressShot = -1;
            beginGesture(shot, pressX, pressY, false);
        }
    }

    private boolean onScroll(Screen screen, double mouseX, double mouseY, double horizontal, double vertical) {
        if (autoScroll) stopAutoScroll();
        if (viewer >= 0) {
            if (ctrlDown) {
                if (vertical != 0) {
                    zoomAt(zoomTarget * (float) Math.pow(ZOOM_STEP, vertical), mouseX, mouseY);
                }
                return false;
            }
            stepViewer(vertical > 0 ? -1 : 1);
            return false;
        }
        if (inGallery(mouseX, mouseY)) {
            scrollTarget -= (float) Math.signum(vertical) * WHEEL_STEP;
            clampScroll();
            return false;
        }
        return true;
    }

    private boolean onKeyRelease(Screen screen, KeyEvent event) {
        int key = event.key();
        ctrlDown = key != InputConstants.KEY_LCONTROL && key != InputConstants.KEY_RCONTROL
                && (event.modifiers() & MOD_CONTROL) != 0;
        return true;
    }

    private boolean onKey(Screen screen, KeyEvent event) {
        int key = event.key();
        ctrlDown = key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
                || (event.modifiers() & MOD_CONTROL) != 0;

        if (autoScroll && key == InputConstants.KEY_ESCAPE) {
            stopAutoScroll();
            return false;
        }

        if (viewer >= 0) {
            if (key == InputConstants.KEY_ESCAPE) {
                closeViewer();
                return false;
            }
            if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_UP) {
                stepViewer(-1);
                return false;
            }
            if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_DOWN) {
                stepViewer(1);
                return false;
            }
            if (key == InputConstants.KEY_HOME) {
                stepViewer(-viewer);
                return false;
            }
            if (key == InputConstants.KEY_END) {
                stepViewer(shots.size() - 1 - viewer);
                return false;
            }
            if (key == InputConstants.KEY_DELETE) {
                deleteSelected();
                return false;
            }
            if (key == InputConstants.KEY_0) {
                panning = false;
                zoomAtCenter(ZOOM_MIN);
                return false;
            }
            if (key == InputConstants.KEY_EQUALS) {
                zoomAtCenter(zoomTarget * ZOOM_STEP);
                return false;
            }
            if (key == InputConstants.KEY_MINUS) {
                zoomAtCenter(zoomTarget / ZOOM_STEP);
                return false;
            }
            return true;
        }

        if (shots.isEmpty()) return true;

        if (key == InputConstants.KEY_ESCAPE && selectMode) {
            exitSelectMode();
            return false;
        }
        if (key == InputConstants.KEY_SPACE && selected >= 0) {
            toggleCheck(selected);
            return false;
        }

        int from = Math.max(selected, 0);
        int target = -1;
        if (key == InputConstants.KEY_LEFT) target = selected < 0 ? 0 : from - 1;
        else if (key == InputConstants.KEY_RIGHT) target = selected < 0 ? 0 : from + 1;
        else if (key == InputConstants.KEY_UP) target = selected < 0 ? 0 : from - columns;
        else if (key == InputConstants.KEY_DOWN) target = selected < 0 ? 0 : from + columns;
        else if (key == InputConstants.KEY_HOME) target = 0;
        else if (key == InputConstants.KEY_END) target = shots.size() - 1;
        boolean isNavigation = key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT
                || key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN
                || key == InputConstants.KEY_HOME || key == InputConstants.KEY_END;
        if (isNavigation) {
            select(Math.max(0, Math.min(target, shots.size() - 1)), true);
            return false;
        }
        if (key == InputConstants.KEY_RETURN && selected >= 0) {
            openViewer(selected);
            return false;
        }
        if (key == InputConstants.KEY_DELETE) {
            if (selectMode && !checked.isEmpty()) {
                deleteChecked();
                return false;
            }
            if (selected >= 0) {
                deleteSelected();
                return false;
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        ScreenshotTextures.clear();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    // ---------- render ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        long nowNanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min((nowNanos - lastNanos) / 1_000_000_000f, 0.1f);
        lastNanos = nowNanos;
        frameDt = dt;
        pendingTooltip = null;
        mouseNowX = mouseX;
        mouseNowY = mouseY;
        updateLongPress(mouseX, mouseY);
        updateGesture(mouseX, mouseY, dt);
        if (viewer >= 0) {
            barDragging = false;
            stopAutoScroll();
        }
        updateBarDrag(mouseY);
        updateAutoScroll(mouseY, dt);
        selectAnim += ((selectMode ? 1f : 0f) - selectAnim) * Math.min(1f, dt * 14f);

        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 18f));
        if (Math.abs(scrollTarget - scroll) < 0.3f) scroll = scrollTarget;

        drawHeader(g, mouseX, mouseY);
        drawGalleryPanel(g, mouseX, mouseY, dt);
        if (showDetail) drawDetailPanel(g, mouseX, mouseY, dt);
        if (autoScroll) {
            drawAutoScrollAnchor(g);
            Cursors.resizeVertical(g);
            pendingTooltip = null;
        }
        if (viewer >= 0) drawViewer(g, mouseX, mouseY);

        if (pendingTooltip != null) {
            HelpTip.renderTooltip(g, this.font, Component.literal(pendingTooltip), mouseX, mouseY,
                    this.width, this.height);
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int titleWidth = this.font.width(this.title);
        int total = Icons.CAMERA_WIDTH + 7 + titleWidth;
        int x = (this.width - total) / 2;
        Icons.camera(g, x, 11, Theme.subtle(), Theme.panelOpaque(), Theme.accent());
        g.text(this.font, this.title, x + Icons.CAMERA_WIDTH + 7, 12, WHITE, true);

        Component subtitle;
        int subtitleColor = Theme.muted();
        if (loading) {
            subtitle = Component.translatable("quickactions.shots.subtitle.loading");
        } else if (selectMode) {
            subtitle = Component.translatable("quickactions.shots.subtitle.selected",
                    checked.size(), shots.size(), UiText.formatBytes(checkedBytes));
            subtitleColor = Theme.accentLight();
        } else {
            subtitle = Component.translatable("quickactions.shots.subtitle", shots.size(), UiText.formatBytes(totalBytes));
        }
        g.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2,
                12 + this.font.lineHeight + 3, subtitleColor, true);

        if (pillVisible()) drawSelectPill(g, mouseX, mouseY);
    }

    private void drawSelectPill(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Rect r = selectPillRect();
        boolean hovered = viewer < 0 && r.contains(mouseX, mouseY);
        pillHover += ((hovered ? 1f : 0f) - pillHover) * 0.35f;

        int border = lerpColor(selectMode ? Theme.accentSoft() : Theme.border(), Theme.accent(), pillHover);
        int fill = selectMode ? lerpColor(Theme.accentMix(0.24f), Theme.accentMix(0.32f), pillHover) : lerpColor(Theme.card(), Theme.cardHover(), pillHover);
        g.fill(r.x() + 1, r.y(), r.x() + r.w() - 1, r.y() + r.h(), border);
        g.fill(r.x(), r.y() + 1, r.x() + r.w(), r.y() + r.h() - 1, border);
        g.fill(r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, fill);

        int cx = r.x() + 9;
        int cy = r.y() + r.h() / 2;
        if (selectMode) {
            Icons.cross(g, cx - 3, cy - 3, 6, WHITE);
        } else {
            Icons.ringCircle(g, cx, cy, 4, 1, Theme.accentLight());
        }
        Component label = Component.translatable(selectMode
                ? "quickactions.shots.select.cancel" : "quickactions.shots.select");
        g.text(this.font, label, r.x() + 17, r.y() + (r.h() - this.font.lineHeight) / 2 + 1,
                selectMode ? WHITE : lerpColor(Theme.muted(), WHITE, pillHover), true);
    }

    private void drawGalleryPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        int right = galleryLeft + galleryWidth;
        g.fill(galleryLeft - 1, PANEL_TOP - 1, right + 1, panelBottom + 1, Theme.border());
        g.fill(galleryLeft, PANEL_TOP, right, panelBottom, Theme.panelBg());

        if (loading) {
            drawLoading(g);
            return;
        }
        if (shots.isEmpty()) {
            drawCentered(g, galleryLeft, galleryWidth,
                    Component.translatable(loadFailed ? "quickactions.shots.error.title" : "quickactions.shots.empty.title"),
                    Component.translatable(loadFailed ? "quickactions.shots.error.hint" : "quickactions.shots.empty.hint"));
            return;
        }

        int hoveredShot = viewer >= 0 ? -1 : shotAt(mouseX, mouseY);
        hoverShot = hoveredShot;
        hoverZone = hoveredShot >= 0 ? zoneAt(hoveredShot, mouseX, mouseY) : ZONE_NONE;
        float follow = Math.min(1f, dt * 14f);
        for (int i = 0; i < hover.length; i++) {
            hover[i] += ((i == hoveredShot ? 1f : 0f) - hover[i]) * follow;
            float target = checked.isEmpty() ? 0f : (checked.contains(shots.get(i).file()) ? 1f : 0f);
            pick[i] += (target - pick[i]) * follow;
        }

        int offset = Math.round(scroll);
        Clip.begin(g, galleryLeft, PANEL_TOP, right, panelBottom);
        for (int ci = 0; ci < cells.size(); ci++) {
            Cell cell = cells.get(ci);
            int y = PANEL_TOP + cell.y() - offset;
            if (y + cell.h() < PANEL_TOP) continue;
            if (y > panelBottom) break;
            int x = galleryLeft + cell.x();
            if (cell.isHeader()) {
                drawDayHeader(g, ci, cell, x, y);
            } else {
                drawCard(g, cell.shot(), x, y);
            }
        }
        drawMarquee(g);
        Clip.end(g);

        drawScrollbar(g, right - SCROLLBAR_WIDTH + 1, mouseX, mouseY, dt);
        updateCardTooltip(hoveredShot);
    }

    private void drawLoading(GuiGraphicsExtractor g) {
        Component text = Component.translatable("quickactions.shots.loading");
        int centerX = galleryLeft + galleryWidth / 2;
        int centerY = PANEL_TOP + viewportHeight() / 2;
        g.text(this.font, text, centerX - this.font.width(text) / 2, centerY - 14, Theme.muted(), true);

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

    private void drawCentered(GuiGraphicsExtractor g, int left, int width, Component title, Component hint) {
        int centerY = PANEL_TOP + viewportHeight() / 2;
        Icons.camera(g, left + width / 2 - Icons.CAMERA_WIDTH / 2, centerY - 34, Theme.scroll(), Theme.panelBg(), Theme.scroll());
        g.text(this.font, title, left + (width - this.font.width(title)) / 2, centerY - 12, WHITE, true);
        String hintText = UiText.fitEnd(this.font, hint.getString(), width - 24);
        g.text(this.font, Component.literal(hintText), left + (width - this.font.width(hintText)) / 2,
                centerY + 2, Theme.muted(), true);
    }

    private void drawDayHeader(GuiGraphicsExtractor g, int headerIndex, Cell cell, int x, int y) {
        g.fill(x, y + 4, x + 2, y + cell.h() - 4, Theme.accent());

        boolean over = viewer < 0 && inGallery(mouseNowX, mouseNowY)
                && mouseNowX >= x && mouseNowX < x + cell.w() && mouseNowY >= y && mouseNowY < y + cell.h();
        float visible = Math.max(selectAnim, over ? 0.9f : 0f);
        int textX = x + 8;
        if (visible > 0.02f || selectMode) {
            int state = dayState(headerIndex);
            int cx = x + 4 + HEADER_CHECK_RADIUS + 2;
            int cy = y + cell.h() / 2;
            boolean overCircle = over && headerCheckRect(cell).contains(mouseNowX, mouseNowY);
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
        textX = x + HEADER_LABEL_OFFSET + 2;

        Component label = cell.label();
        g.text(this.font, label, textX, y + (cell.h() - this.font.lineHeight) / 2, Theme.accentLight(), true);
        String count = String.valueOf(cell.count());
        int labelRight = textX + this.font.width(label) + 8;
        int pillWidth = this.font.width(count) + 10;
        g.fill(labelRight, y + 4, labelRight + pillWidth, y + cell.h() - 4, Theme.tile());
        g.text(this.font, Component.literal(count), labelRight + 5, y + (cell.h() - this.font.lineHeight) / 2,
                Theme.muted(), true);
        g.fill(labelRight + pillWidth + 8, y + cell.h() / 2, x + cell.w(), y + cell.h() / 2 + 1, Theme.borderSoft());
    }

    private void drawCard(GuiGraphicsExtractor g, int shot, int x, int y) {
        ScreenshotInfo info = shots.get(shot);
        boolean isSelected = shot == selected;
        boolean isChecked = !checked.isEmpty() && checked.contains(info.file());
        float hv = hover[shot];
        float pk = pick[shot];
        float mark = Math.max(pk, isSelected ? 1f : 0f);

        if (isSelected) {
            g.fill(x - 4, y - 4, x + cardWidth + 4, y + cardHeight + 4, Theme.accentAlpha(0x22));
            g.fill(x - 2, y - 2, x + cardWidth + 2, y + cardHeight + 2, Theme.accent());
        } else if (isChecked) {
            g.fill(x - 2, y - 2, x + cardWidth + 2, y + cardHeight + 2, Theme.accentSoft());
        } else {
            g.fill(x - 1, y - 1, x + cardWidth + 1, y + cardHeight + 1, lerpColor(Theme.border(), Theme.accentSoft(), hv));
        }
        g.fill(x, y, x + cardWidth, y + cardHeight, lerpColor(lerpColor(Theme.card(), Theme.cardHover(), hv), Theme.accentSelected(), mark));

        int inset = Math.round(pk * CHECKED_INSET);
        int tx = x + 1;
        int ty = y + 1;
        drawThumb(g, info, tx + inset, ty + inset, thumbWidth - inset * 2, thumbHeight - inset * 2);
        if (hv > 0.01f && !isChecked) {
            g.fill(tx, ty, tx + thumbWidth, ty + thumbHeight, withAlpha(0xFFFFFF, hv * 0.10f));
        }

        String caption = info.vanillaName() ? info.time().format(TIME_FORMAT) : info.name();
        caption = UiText.fitEnd(this.font, caption, cardWidth - 12);
        g.text(this.font, Component.literal(caption), x + 6, ty + thumbHeight + 4,
                mark > 0.5f ? WHITE : lerpColor(Theme.muted(), WHITE, hv), true);

        drawCheckCircle(g, shot, tx, ty, hv, pk);
        if (selectAnim > 0.02f) drawExpandButton(g, shot, tx, ty);
    }

    private void drawCheckCircle(GuiGraphicsExtractor g, int shot, int tx, int ty, float hv, float pk) {
        float press = 0f;
        if (shot == pressShot) {
            long held = System.currentTimeMillis() - pressMillis - PRESS_FEEDBACK_DELAY_MS;
            press = Math.max(0f, Math.min(1f, held / (float) (LONG_PRESS_MS - PRESS_FEEDBACK_DELAY_MS)));
        }
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
        int ex = tx + thumbWidth - EXPAND_SIZE - EXPAND_PAD;
        int ey = ty + thumbHeight - EXPAND_SIZE - EXPAND_PAD;
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
        int pxW = ((thumbWidth * scale + 31) / 32) * 32;
        int pxH = Math.max(1, Math.round(pxW * thumbHeight / (float) thumbWidth));

        Loaded loaded = ScreenshotTextures.get(info, Mode.FILL, pxW, pxH);
        if (loaded != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, loaded.id(), x, y, 0, 0, w, h, w, h);
            float fade = 1f - Math.min(1f, (System.currentTimeMillis() - loaded.readyMillis()) / (float) THUMB_FADE_MS);
            if (fade > 0f) {
                g.fill(x, y, x + w, y + h, withAlpha(Theme.tone(0.022f), fade));
            }
        } else if (ScreenshotTextures.failed(info, Mode.FILL, pxW, pxH)) {
            drawBroken(g, x, y, w, h);
        } else {
            drawShimmer(g, x, y, w, h);
        }
    }

    private void drawShimmer(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Theme.tone(0.022f));
        float phase = (System.currentTimeMillis() % 1500L) / 1500f;
        int band = Math.max(8, w / 3);
        int bandX = x - band + (int) ((w + band * 2) * phase);
        int from = Math.max(x, bandX);
        int to = Math.min(x + w, bandX + band);
        if (to > from) g.fill(from, y, to, y + h, 0x14FFFFFF);
    }

    private void drawBroken(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BROKEN_TILE);
        g.text(this.font, Component.literal("?"), x + (w - this.font.width("?")) / 2,
                y + (h - this.font.lineHeight) / 2, BROKEN_TEXT, true);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int mouseX, int mouseY, float dt) {
        int[] geo = scrollbarGeometry();
        if (geo == null) {
            barHover = 0f;
            return;
        }
        boolean hot = barDragging || (viewer < 0 && !autoScroll && overScrollbar(mouseX, mouseY));
        barHover += ((hot ? 1f : 0f) - barHover) * Math.min(1f, dt * 16f);

        int left = x - Math.round(barHover * 2f);
        int right = x + 3;
        g.fill(left, geo[0], right, geo[0] + geo[1], withAlpha(0xFFFFFF, 0.13f + 0.06f * barHover));
        int color = barDragging ? Theme.accent() : lerpColor(Theme.scroll(), Theme.tone(0.48f), barHover);
        g.fill(left, geo[3], right, geo[3] + geo[2], color);
    }

    private void updateCardTooltip(int hoveredShot) {
        if (hoveredShot >= 0 && hoverZone == ZONE_EXPAND) {
            pendingTooltip = Component.translatable("quickactions.shots.expand.tooltip").getString();
            return;
        }
        long now = System.currentTimeMillis();
        if (hoveredShot != tooltipShot) {
            tooltipShot = hoveredShot;
            tooltipSince = now;
        }
        if (hoveredShot < 0 || hoveredShot >= shots.size() || now - tooltipSince < TOOLTIP_DELAY_MS) return;

        ScreenshotInfo info = shots.get(hoveredShot);
        pendingTooltip = info.name() + "\n" + info.time().toLocalDate() + " " + info.time().format(TIME_FORMAT)
                + "  ·  " + UiText.formatBytes(info.sizeBytes());
    }

    // ---------- right panel ----------

    private void drawDetailPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        int left = detailLeft;
        int right = left + DETAIL_WIDTH;
        g.fill(left - 1, PANEL_TOP - 1, right + 1, panelBottom + 1, Theme.border());
        g.fill(left, PANEL_TOP, right, panelBottom, Theme.panelBg());

        ScreenshotInfo info = selectedInfo();
        if (info == null) {
            drawCentered(g, left, DETAIL_WIDTH,
                    Component.translatable("quickactions.shots.detail.empty_title"),
                    Component.translatable("quickactions.shots.detail.empty_hint"));
            return;
        }

        // preview
        Rect box = previewRect();
        boolean overPreview = viewer < 0 && box.contains(mouseX, mouseY);
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
                drawBroken(g, box.x(), box.y(), box.w(), box.h());
            } else {
                drawShimmer(g, box.x(), box.y(), box.w(), box.h());
            }
        }
        if (previewHover > 0.02f) {
            Component view = Component.translatable("quickactions.shots.detail.view");
            int pillWidth = this.font.width(view) + 14;
            int pillX = box.x() + (box.w() - pillWidth) / 2;
            int pillY = box.y() + box.h() - 18;
            g.fill(pillX, pillY, pillX + pillWidth, pillY + 13, withAlpha(0x000000, 0.72f * previewHover));
            g.text(this.font, view, pillX + 7, pillY + 3, withAlpha(0xFFFFFF, previewHover), true);
        }

        // name and date
        int textLeft = left + 12;
        int textWidth = DETAIL_WIDTH - 24;
        int y = box.y() + box.h() + 12;
        String name = UiText.fitEnd(this.font, info.name(), textWidth);
        g.text(this.font, Component.literal(name), textLeft, y, WHITE, true);
        if (!name.equals(info.name()) && mouseX >= textLeft && mouseX < textLeft + textWidth
                && mouseY >= y - 2 && mouseY < y + this.font.lineHeight + 2) {
            pendingTooltip = info.name(); // long name is cut, full one on hover
        }
        y += this.font.lineHeight + 3;
        Component date = Component.translatable("quickactions.shots.detail.date",
                fullDate(info.time().toLocalDate()), info.time().format(TIME_FORMAT));
        g.text(this.font, date, textLeft, y, Theme.muted(), true);
        y += this.font.lineHeight + 8;
        g.fill(left + 10, y, right - 10, y + 1, Theme.border());
        y += 8;

        // "name: value" rows
        y = drawInfoRow(g, left, right, y, "quickactions.shots.detail.resolution", resolutionText(info));
        y = drawInfoRow(g, left, right, y, "quickactions.shots.detail.size", UiText.formatBytes(info.sizeBytes()));
        drawInfoRow(g, left, right, y, "quickactions.shots.detail.format", formatText(info));

        // hint at panel bottom
        List<String> tips = UiText.wrap(this.font,
                Component.translatable(selectMode
                        ? "quickactions.shots.detail.tips_select" : "quickactions.shots.detail.tips").getString(),
                DETAIL_WIDTH - 24);
        int tipY = panelBottom - 8 - tips.size() * (this.font.lineHeight + 2);
        for (String line : tips) {
            g.text(this.font, Component.literal(line), textLeft, tipY, Theme.tone(0.34f), true);
            tipY += this.font.lineHeight + 2;
        }
    }

    private int drawInfoRow(GuiGraphicsExtractor g, int left, int right, int y, String labelKey, String value) {
        Component label = Component.translatable(labelKey);
        g.text(this.font, label, left + 12, y, Theme.muted(), true);
        g.text(this.font, Component.literal(value), right - 12 - this.font.width(value), y, WHITE, true);
        return y + this.font.lineHeight + 7;
    }

    private String resolutionText(ScreenshotInfo info) {
        int[] size = sizeCache.computeIfAbsent(info.file(), file -> {
            int[] read = FileOps.readPngSize(file);
            return read != null ? read : NO_SIZE;
        });
        return size.length == 2 ? size[0] + " × " + size[1] : "—";
    }

    private String formatText(ScreenshotInfo info) {
        String name = info.name();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toUpperCase(java.util.Locale.ROOT) : "—";
    }

    // ---------- fullscreen view ----------

    private void drawViewer(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (viewer < 0 || viewer >= shots.size()) return;
        ScreenshotInfo info = shots.get(viewer);
        float open = Math.min(1f, (System.currentTimeMillis() - viewerOpenedMillis) / (float) VIEWER_FADE_MS);

        g.nextStratum(); // above screen buttons and panels
        g.fill(0, 0, this.width, this.height, withAlpha(VIEWER_BACKDROP, 0.94f * open));

        // image area
        int areaLeft = 56;
        int areaTop = 44;
        int areaWidth = this.width - areaLeft * 2;
        int areaHeight = this.height - areaTop - 34;
        int scale = guiScale();
        int pxW = areaWidth * scale;
        int pxH = areaHeight * scale;

        Loaded loaded = ScreenshotTextures.get(info, Mode.FIT, pxW, pxH);
        int w;
        int h;
        if (loaded != null) {
            w = Math.min(areaWidth, loaded.width() / scale);
            h = Math.min(areaHeight, loaded.height() / scale);
            prefetchNeighbours(pxW, pxH);
        } else {
            w = Math.min(areaWidth, areaHeight * 16 / 9);
            h = w * 9 / 16;
        }
        int x = areaLeft + (areaWidth - w) / 2;
        int y = areaTop + (areaHeight - h) / 2;
        viewerImage = new Rect(x, y, w, h);
        if (!info.file().equals(zoomFile)) {
            resetZoomInstant();
            zoomFile = info.file();
        }
        updateZoom(mouseX, mouseY);

        g.fill(x + 4, y + 5, x + w + 4, y + h + 5, withAlpha(0x000000, 0.4f * open));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, withAlpha(Theme.border() & 0xFFFFFF, open));
        g.fill(x, y, x + w, y + h, Theme.deep());
        if (loaded != null) {
            // zoomed: switch to the full resolution texture as soon as it is ready
            Loaded sharp = zoom > 1.001f || zoomTarget > 1.001f
                    ? ScreenshotTextures.get(info, Mode.FIT, ZOOM_TEXTURE_SIDE, ZOOM_TEXTURE_SIDE) : null;
            drawZoomedImage(g, sharp != null ? sharp : loaded, x, y, w, h);
        } else {
            Loaded fallback = ScreenshotTextures.fallback(info);
            if (fallback != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, fallback.id(), x, y, 0, 0, w, h, w, h);
            } else if (ScreenshotTextures.failed(info, Mode.FIT, pxW, pxH)) {
                drawBroken(g, x, y, w, h);
            } else {
                drawShimmer(g, x, y, w, h);
            }
        }
        if (open < 1f) {
            g.fill(x, y, x + w, y + h, withAlpha(VIEWER_BACKDROP, 1f - open));
        }

        // top bar: name, date, counter, close
        String name = UiText.fitEnd(this.font, info.name(), this.width - 190);
        g.text(this.font, Component.literal(name), 16, 12, withAlpha(0xFFFFFF, open), true);
        Component date = Component.translatable("quickactions.shots.detail.date",
                fullDate(info.time().toLocalDate()), info.time().format(TIME_FORMAT));
        g.text(this.font, date, 16, 12 + this.font.lineHeight + 3, withAlpha(Theme.muted() & 0xFFFFFF, open), true);

        Component counter = Component.translatable("quickactions.shots.viewer.counter", viewer + 1, shots.size());
        Rect close = closeRect();
        g.text(this.font, counter, close.x() - 12 - this.font.width(counter), 12 + 2, withAlpha(Theme.textSoft() & 0xFFFFFF, open), true);
        drawRoundButton(g, close, close.contains(mouseX, mouseY));
        Icons.cross(g, close.x() + 7, close.y() + 7, 8, WHITE);

        // arrows
        drawArrow(g, prevRect(), true, viewer > 0, mouseX, mouseY);
        drawArrow(g, nextRect(), false, viewer < shots.size() - 1, mouseX, mouseY);

        // zoom indicator, mini map, cursor
        if (loaded != null) {
            drawZoomHud(g, x, y, w, h);
            drawMiniMap(g, loaded, x, y, w, h);
            if (zoomTarget > ZOOM_MIN + 0.001f && viewerImage.contains(mouseX, mouseY)) {
                if (panning) Cursors.move(g);
                else Cursors.hand(g);
            }
        }

        // key hints
        Component hint = zoomTarget > ZOOM_MIN + 0.001f
                ? Component.translatable("quickactions.shots.viewer.hint.zoomed")
                : Component.empty()
                        .append(Component.translatable("quickactions.shots.viewer.hint"))
                        .append(Component.literal("   \u00b7   "))
                        .append(Component.translatable("quickactions.shots.viewer.hint.zoom"));
        g.text(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height - 18,
                withAlpha(Theme.tone(0.34f) & 0xFFFFFF, open), true);
    }

    private void drawZoomedImage(GuiGraphicsExtractor g, Loaded tex, int x, int y, int w, int h) {
        int zw = Math.max(w, Math.round(w * zoom));
        int zh = Math.max(h, Math.round(h * zoom));
        int left = Math.round(x + (w - zw) / 2f + panX);
        int top = Math.round(y + (h - zh) / 2f + panY);
        boolean zoomed = zw != w || zh != h;
        if (zoomed) Clip.begin(g, x, y, x + w, y + h);
        g.blit(RenderPipelines.GUI_TEXTURED, tex.id(), left, top, 0, 0, zw, zh, zw, zh);
        if (zoomed) Clip.end(g);
    }

    /** rounded plate: 3 fills that never overlap, so translucent colors stay even */
    private static void fillPlate(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
    }

    private void drawZoomHud(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        float a = zoomHud;
        int pw = 76;
        int ph = 27;
        if (a < 0.02f || w < pw + 24 || h < ph + 24) return;
        int px = x + (w - pw) / 2;
        int py = y + h - ph - 12;

        fillPlate(g, px - 1, py - 1, pw + 2, ph + 2, withAlpha(Theme.borderStrong() & 0xFFFFFF, a));
        fillPlate(g, px, py, pw, ph, withAlpha(Theme.deep() & 0xFFFFFF, 0.94f * a));

        boolean atMax = zoom >= ZOOM_MAX - 0.01f;
        String pct = Math.round(zoom * 100f) + "%";
        int color = atMax ? Theme.accentLight() : Theme.textBright();
        g.text(this.font, Component.literal(pct), px + (pw - this.font.width(pct)) / 2, py + 6,
                withAlpha(color & 0xFFFFFF, a), true);

        // log scale bar: 100% .. 800%
        int bx = px + 9;
        int bw = pw - 18;
        int by = py + ph - 8;
        g.fill(bx, by, bx + bw, by + 2, withAlpha(Theme.tone(0.2f) & 0xFFFFFF, a));
        float t = clampF((float) (Math.log(Math.max(zoom, 1f)) / Math.log(ZOOM_MAX)), 0f, 1f);
        int filled = Math.round(bw * t);
        if (filled > 0) g.fill(bx, by, bx + filled, by + 2, withAlpha(Theme.accent() & 0xFFFFFF, a));
    }

    private void drawMiniMap(GuiGraphicsExtractor g, Loaded base, int x, int y, int w, int h) {
        float a = zoomHud;
        if (a < 0.02f || zoom <= ZOOM_MIN + 0.01f || w < 180 || h < 110) return;
        int mw = Math.max(40, Math.min(72, w / 5));
        int mh = Math.max(24, Math.round(mw * (h / (float) w)));
        int mx = x + w - mw - 14;
        int my = y + h - mh - 14;

        fillPlate(g, mx - 1, my - 1, mw + 2, mh + 2, withAlpha(Theme.borderStrong() & 0xFFFFFF, a));
        g.blit(RenderPipelines.GUI_TEXTURED, base.id(), mx, my, 0, 0, mw, mh, mw, mh);
        g.fill(mx, my, mx + mw, my + mh, withAlpha(0x000000, 0.5f * a));
        g.fill(mx, my, mx + mw, my + mh, withAlpha(VIEWER_BACKDROP, 1f - a)); // fades the thumb itself

        float vw = 1f / zoom;
        float cx = 0.5f - panX / (w * zoom);
        float cy = 0.5f - panY / (h * zoom);
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

    private void prefetchNeighbours(int pxW, int pxH) {
        if (viewer > 0) ScreenshotTextures.get(shots.get(viewer - 1), Mode.FIT, pxW, pxH);
        if (viewer < shots.size() - 1) ScreenshotTextures.get(shots.get(viewer + 1), Mode.FIT, pxW, pxH);
    }

    private void drawRoundButton(GuiGraphicsExtractor g, Rect r, boolean hovered) {
        int border = hovered ? Theme.accent() : Theme.border();
        int fill = hovered ? Theme.accentDeep() : Theme.card();
        g.fill(r.x() + 1, r.y(), r.x() + r.w() - 1, r.y() + r.h(), border);
        g.fill(r.x(), r.y() + 1, r.x() + r.w(), r.y() + r.h() - 1, border);
        g.fill(r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, fill);
    }

    private void drawArrow(GuiGraphicsExtractor g, Rect r, boolean left, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && r.contains(mouseX, mouseY);
        drawRoundButton(g, r, hovered);
        int color = enabled ? WHITE : Theme.scroll();
        Icons.chevron(g, r.x() + (r.w() - Icons.CHEVRON_WIDTH) / 2 + (left ? -1 : 1),
                r.y() + (r.h() - Icons.CHEVRON_HEIGHT) / 2, left, color);
    }

    // ---------- colors ----------

    private static int lerpColor(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - k) + ((to >>> 24) & 0xFF) * k);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int gr = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** RGB + alpha 0..1 -> ARGB */
    private static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
