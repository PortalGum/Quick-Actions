package com.quickactions.ui.settings;

import com.mojang.blaze3d.platform.InputConstants;
import com.quickactions.actions.pack.preset.PresetStore;
import com.quickactions.actions.screenshot.ScreenshotLibrary;
import com.quickactions.actions.server.ServerListTransfer;
import com.quickactions.actions.world.backup.BackupManager;
import com.quickactions.config.QuickActionsConfig;
import com.quickactions.ui.Clip;
import com.quickactions.ui.Cursors;
import com.quickactions.ui.Icons;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.UiText;
import com.quickactions.ui.notify.Notifications;
import com.quickactions.ui.theme.Colors;
import com.quickactions.ui.theme.Theme;
import com.quickactions.util.ClientUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SettingsScreen extends Screen {
    // ---------- layout ----------
    private static final int PANEL_TOP = 40;
    private static final int FOOTER = 40;
    private static final int PANEL_MAX_WIDTH = 348;
    private static final int PAD = 8;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_HIT = 10;
    private static final int ITEM_GAP = 4;
    private static final int SECTION_GAP = 12;
    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_TILE = 24;
    private static final int MASTER_TILE = 30;
    private static final int ROW_SWITCH_W = 29;
    private static final int ROW_SWITCH_H = 15;
    private static final int MASTER_SWITCH_W = 37;
    private static final int MASTER_SWITCH_H = 19;
    private static final int WHEEL_STEP = 46;
    private static final long APPEAR_STAGGER_MS = 45;
    private static final long APPEAR_MS = 260;
    private static final long RESET_CONFIRM_MS = 3000;
    private static final float DIMMED = 0.42f;

    private static final int WHITE = 0xFFFFFFFF;

    private enum Kind {HEADER, MASTER, ROW, LINK}

    private enum Trailing {FOLDER, THEME}

    private static final class Item {
        final Kind kind;
        final String id;
        final String[] icon;
        final BooleanSupplier value;
        final Consumer<Boolean> setter;
        final BooleanSupplier active;
        Runnable action;
        Trailing trailing = Trailing.FOLDER;
        String detail;
        List<String> lines = List.of();
        int y;
        int h;
        float anim;
        float hover;
        float dim = 1f;
        float flash;

        Item(Kind kind, String id, String[] icon, BooleanSupplier value, Consumer<Boolean> setter,
             BooleanSupplier active) {
            this.kind = kind;
            this.id = id;
            this.icon = icon;
            this.value = value;
            this.setter = setter;
            this.active = active;
            this.anim = value.getAsBoolean() ? 1f : 0f; // toggles are already in place on open
        }
    }

    private final Screen parent;
    private final List<Item> items = new ArrayList<>();
    private final long openedMillis = System.currentTimeMillis();

    private int panelLeft;
    private int panelWidth;
    private int panelBottom;
    private int innerWidth;
    private int contentHeight;

    private float scroll;
    private float scrollTarget;
    private long lastNanos;
    private float gearAngle;

    private boolean barDragging;
    private int barGrab;
    private float barHover;

    private Button resetButton;
    private boolean resetArmed;
    private long resetArmedUntil;

    private float drawDim = 1f;
    private float drawAppear = 1f;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("quickactions.settings.title"));
        this.parent = parent;
    }

    private static QuickActionsConfig cfg() {
        return QuickActionsConfig.get();
    }

    // ---------- settings list ----------

    private void buildItems() {
        items.clear();
        items.add(new Item(Kind.MASTER, "master", SettingIcons.POWER,
                () -> cfg().enabled, v -> cfg().enabled = v, () -> true));

        items.add(header("section.menus"));
        items.add(row("world", SettingIcons.WORLD,
                () -> cfg().showInWorldSelection, v -> cfg().showInWorldSelection = v, () -> cfg().enabled));
        items.add(row("server", SettingIcons.SERVER,
                () -> cfg().showInServerList, v -> cfg().showInServerList = v, () -> cfg().enabled));
        items.add(row("pack", SettingIcons.PACK,
                () -> cfg().showInResourcePacks, v -> cfg().showInResourcePacks = v, () -> cfg().enabled));
        items.add(row("screenshot", SettingIcons.CAMERA,
                () -> cfg().showInScreenshotViewer, v -> cfg().showInScreenshotViewer = v, () -> cfg().enabled));

        items.add(header("section.drag"));
        items.add(row("server_drag", SettingIcons.REORDER,
                () -> cfg().serverDragReorder, v -> cfg().serverDragReorder = v,
                () -> cfg().enabled && cfg().showInServerList));
        items.add(row("pack_drag", SettingIcons.REORDER,
                () -> cfg().packDragReorder, v -> cfg().packDragReorder = v,
                () -> cfg().enabled && cfg().showInResourcePacks));

        items.add(header("section.main"));
        items.add(row("screenshot_button", SettingIcons.WINDOW,
                () -> cfg().showScreenshotButton, v -> cfg().showScreenshotButton = v, () -> true));

        items.add(header("section.updates"));
        items.add(row("update_check", SettingIcons.UPDATE,
                () -> cfg().checkForUpdates, v -> cfg().checkForUpdates = v, () -> true));

        items.add(header("section.interface"));
        items.add(link("theme", SettingIcons.PALETTE, Trailing.THEME, null,
                () -> Minecraft.getInstance().setScreenAndShow(new ThemeScreen(this))));

        items.add(header("section.folders"));
        items.add(folder("folder_backups", SettingIcons.BACKUP, BackupManager.rootDir()));
        items.add(folder("folder_servers", SettingIcons.SERVER, ServerListTransfer.directory()));
        items.add(folder("folder_presets", SettingIcons.PACK, PresetStore.directory()));
        items.add(folder("folder_screenshots", SettingIcons.CAMERA, ScreenshotLibrary.directory()));
    }

    private static Item link(String id, String[] icon, Trailing trailing, String detail, Runnable action) {
        Item item = new Item(Kind.LINK, id, icon, () -> false, v -> { }, () -> true);
        item.trailing = trailing;
        item.detail = detail;
        item.action = action;
        return item;
    }

    private static Item folder(String id, String[] icon, Path path) {
        return link(id, icon, Trailing.FOLDER, path.toAbsolutePath().toString(), () -> {
            if (!ClientUtil.openFolder(path)) Notifications.failure();
        });
    }

    private static Item header(String id) {
        return new Item(Kind.HEADER, id, new String[0], () -> false, v -> { }, () -> true);
    }

    private static Item row(String id, String[] icon, BooleanSupplier value, Consumer<Boolean> setter,
                            BooleanSupplier active) {
        return new Item(Kind.ROW, id, icon, value, setter, active);
    }

    private static String title(Item item) {
        return Component.translatable("quickactions.settings." + item.id).getString();
    }

    private static String description(Item item) {
        return Component.translatable("quickactions.settings." + item.id + ".desc").getString();
    }

    // ---------- init ----------

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 16);
        panelLeft = (this.width - panelWidth) / 2;
        panelBottom = this.height - FOOTER;
        innerWidth = panelWidth - PAD * 2 - SCROLLBAR_WIDTH;
        barDragging = false;

        if (items.isEmpty()) buildItems();
        layout();

        int buttonWidth = Math.min(150, (this.width - 24 - 4) / 2);
        int left = this.width / 2 - buttonWidth - 2;
        resetButton = Button.builder(Component.translatable("quickactions.settings.button.reset"),
                b -> onResetClicked()).bounds(left, this.height - 28, buttonWidth, 20).build();
        addRenderableWidget(resetButton);
        addRenderableWidget(Button.builder(Component.translatable("quickactions.settings.button.done"),
                b -> onClose()).bounds(left + buttonWidth + 4, this.height - 28, buttonWidth, 20).build());
        resetArmed = false;

        ScreenMouseEvents.allowMouseClick(this).register(this::onClick);
        ScreenMouseEvents.allowMouseRelease(this).register(this::onRelease);
        ScreenMouseEvents.allowMouseScroll(this).register(this::onScroll);
        ScreenKeyboardEvents.allowKeyPress(this).register(this::onKey);
    }

    private void layout() {
        int y = PAD;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.kind == Kind.HEADER) {
                if (i > 0) y += SECTION_GAP - ITEM_GAP;
                item.h = HEADER_HEIGHT;
            } else {
                boolean master = item.kind == Kind.MASTER;
                int tile = master ? MASTER_TILE : ROW_TILE;
                int switchWidth = master ? MASTER_SWITCH_W : ROW_SWITCH_W;
                if (item.kind == Kind.LINK) switchWidth = 30;
                int textWidth = innerWidth - (10 + tile + 10) - switchWidth - 22;
                item.lines = UiText.wrap(this.font, description(item), textWidth);
                int block = this.font.lineHeight + 3 + item.lines.size() * (this.font.lineHeight + 1);
                if (item.detail != null) block += this.font.lineHeight + 4;
                item.h = Math.max(master ? 58 : 40, block + (master ? 26 : 20));
            }
            item.y = y;
            y += item.h + ITEM_GAP;
        }
        contentHeight = y - ITEM_GAP + PAD;
        clampScroll();
    }

    private int viewportHeight() {
        return panelBottom - PANEL_TOP;
    }

    private void clampScroll() {
        float max = Math.max(0, contentHeight - viewportHeight());
        scrollTarget = Math.max(0, Math.min(scrollTarget, max));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    // ---------- actions ----------

    private void toggle(Item item) {
        if (item.kind == Kind.LINK) {
            item.flash = 1f;
            if (item.action != null) item.action.run();
            return;
        }
        item.setter.accept(!item.value.getAsBoolean());
        QuickActionsConfig.save();
        item.flash = 1f;
    }

    private void onResetClicked() {
        long now = System.currentTimeMillis();
        if (!resetArmed) {
            resetArmed = true;
            resetArmedUntil = now + RESET_CONFIRM_MS;
            resetButton.setMessage(Component.translatable("quickactions.settings.button.reset_confirm"));
            return;
        }
        resetArmed = false;
        resetButton.setMessage(Component.translatable("quickactions.settings.button.reset"));
        QuickActionsConfig.reset();
        for (Item item : items) {
            if (item.kind != Kind.HEADER) item.flash = 1f;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    // ---------- scrollbar ----------

    private int[] scrollbarGeometry() {
        int viewport = viewportHeight();
        if (contentHeight <= viewport) return null;
        int trackTop = PANEL_TOP + 3;
        int trackHeight = viewport - 6;
        int thumb = Math.max(18, trackHeight * viewport / contentHeight);
        float max = contentHeight - viewport;
        int thumbTop = trackTop + Math.round((trackHeight - thumb) * (scroll / max));
        return new int[]{trackTop, trackHeight, thumb, thumbTop};
    }

    private boolean overScrollbar(double mx, double my) {
        int right = panelLeft + panelWidth;
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

    // ---------- input ----------

    private int itemAt(double mx, double my) {
        if (mx < panelLeft + PAD || mx >= panelLeft + PAD + innerWidth || my < PANEL_TOP || my >= panelBottom) {
            return -1;
        }
        double cy = my - PANEL_TOP + Math.round(scroll);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.kind != Kind.HEADER && cy >= item.y && cy < item.y + item.h) return i;
        }
        return -1;
    }

    /** @return false to eat the click */
    private boolean onClick(Screen screen, MouseButtonEvent event) {
        if (event.button() != MouseButtons.LEFT) return true;
        double mx = event.x();
        double my = event.y();
        if (overScrollbar(mx, my)) {
            beginBarDrag(my);
            return false;
        }
        int index = itemAt(mx, my);
        if (index >= 0) {
            toggle(items.get(index));
            return false;
        }
        return true;
    }

    private boolean onRelease(Screen screen, MouseButtonEvent event) {
        if (event.button() == MouseButtons.LEFT) barDragging = false;
        return true;
    }

    private boolean onScroll(Screen screen, double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= panelLeft && mouseX < panelLeft + panelWidth && mouseY >= PANEL_TOP && mouseY < panelBottom) {
            scrollTarget -= (float) Math.signum(vertical) * WHEEL_STEP;
            clampScroll();
            return false;
        }
        return true;
    }

    private boolean onKey(Screen screen, KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_DOWN) {
            scrollTarget += WHEEL_STEP;
        } else if (key == InputConstants.KEY_UP) {
            scrollTarget -= WHEEL_STEP;
        } else if (key == InputConstants.KEY_HOME) {
            scrollTarget = 0;
        } else if (key == InputConstants.KEY_END) {
            scrollTarget = contentHeight;
        } else {
            return true;
        }
        clampScroll();
        return false;
    }

    // ---------- render ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        long nowNanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min((nowNanos - lastNanos) / 1_000_000_000f, 0.1f);
        lastNanos = nowNanos;
        long elapsed = System.currentTimeMillis() - openedMillis;

        if (resetArmed && System.currentTimeMillis() > resetArmedUntil) {
            resetArmed = false;
            resetButton.setMessage(Component.translatable("quickactions.settings.button.reset"));
        }

        updateBarDrag(mouseY);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 18f));
        if (Math.abs(scrollTarget - scroll) < 0.3f) scroll = scrollTarget;
        gearAngle += dt * 0.8f;

        int hovered = barDragging || overScrollbar(mouseX, mouseY) ? -1 : itemAt(mouseX, mouseY);
        float follow = Math.min(1f, dt * 14f);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.kind == Kind.HEADER) continue;
            item.anim += ((item.value.getAsBoolean() ? 1f : 0f) - item.anim) * Math.min(1f, dt * 12f);
            item.hover += ((i == hovered ? 1f : 0f) - item.hover) * follow;
            item.dim += ((item.active.getAsBoolean() ? 1f : DIMMED) - item.dim) * follow;
            item.flash = Math.max(0f, item.flash - dt * 2.5f);
        }

        drawTitle(g);
        drawPanel(g, mouseX, mouseY, dt, elapsed);
        if (hovered >= 0) Cursors.hand(g);
    }

    private void drawTitle(GuiGraphicsExtractor g) {
        int titleWidth = this.font.width(this.title);
        int x = (this.width - (15 + 7 + titleWidth)) / 2;
        Icons.gear(g, x + 7, 16, 7, gearAngle, Theme.subtle());
        g.text(this.font, this.title, x + 22, 12, WHITE, true);
        Component subtitle = Component.translatable("quickactions.settings.subtitle");
        g.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2,
                12 + this.font.lineHeight + 3, Theme.muted(), true);
    }

    private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt, long elapsed) {
        int right = panelLeft + panelWidth;
        float open = ease(Math.min(1f, elapsed / 220f));

        g.fill(panelLeft - 1, PANEL_TOP - 1, right + 1, panelBottom + 1, scaleAlpha(Theme.border(), open));
        g.fill(panelLeft, PANEL_TOP, right, panelBottom, scaleAlpha(Theme.panelBg(), open));

        int offset = Math.round(scroll);
        int ix = panelLeft + PAD;
        Clip.begin(g, panelLeft, PANEL_TOP, right, panelBottom);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            float appear = ease(clamp01((elapsed - i * APPEAR_STAGGER_MS) / (float) APPEAR_MS));
            int iy = PANEL_TOP + item.y - offset + Math.round((1f - appear) * 10f);
            if (iy + item.h < PANEL_TOP || iy > panelBottom) continue;
            drawAppear = appear;
            drawDim = item.kind == Kind.HEADER ? 1f : item.dim;
            drawItem(g, item, ix, iy);
        }
        drawAppear = 1f;
        drawDim = 1f;

        // soft edges: shows the list continues
        float max = contentHeight - viewportHeight();
        for (int i = 0; i < 12; i++) {
            int shade = withAlpha(Theme.panelOpaque() & 0xFFFFFF, (1f - i / 12f) * 0.95f * open);
            if (scroll > 1f) g.fill(panelLeft, PANEL_TOP + i, right, PANEL_TOP + i + 1, shade);
            if (scroll < max - 1f) g.fill(panelLeft, panelBottom - 1 - i, right, panelBottom - i, shade);
        }
        Clip.end(g);

        drawScrollbar(g, right - SCROLLBAR_WIDTH + 1, mouseX, mouseY, dt);
    }

    private void drawItem(GuiGraphicsExtractor g, Item item, int ix, int iy) {
        if (item.kind == Kind.HEADER) {
            drawSection(g, item, ix, iy);
            return;
        }
        boolean master = item.kind == Kind.MASTER;
        float t = item.anim;
        float hv = item.hover;
        int w = innerWidth;
        int h = item.h;

        // card
        int border = lerpColor(master ? lerpColor(Theme.borderSoft(), Theme.accentSoft(), t * 0.7f) : Theme.borderSoft(), Theme.accentSoft(), hv);
        int fill = lerpColor(master ? Theme.card() : Theme.card(), Theme.cardHover(), hv);
        rrect(g, ix, iy, w, h, c(border), c(fill));
        if (master && t > 0.02f) {
            int slices = 24;
            int sliceWidth = (w - 2 + slices - 1) / slices;
            for (int s = 0; s < slices; s++) {
                int x0 = ix + 1 + s * sliceWidth;
                int x1 = Math.min(x0 + sliceWidth, ix + w - 1);
                if (x0 >= x1) break;
                g.fill(x0, iy + 1, x1, iy + h - 1,
                        withAlpha(Theme.accent(), 0.22f * t * (1f - s / (float) slices) * drawAppear));
            }
        }
        if (item.flash > 0.01f) {
            g.fill(ix + 1, iy + 1, ix + w - 1, iy + h - 1, withAlpha(Theme.accent(), 0.22f * item.flash * drawAppear));
        }
        if (t > 0.02f) {
            g.fill(ix, iy + 5, ix + 2, iy + h - 5, c(lerpColor(Theme.panelOpaque(), Theme.accent(), t)));
        }

        // tile with icon
        int tile = master ? MASTER_TILE : ROW_TILE;
        int tx = ix + 10;
        int ty = iy + (h - tile) / 2;
        boolean link = item.kind == Kind.LINK;
        float u = link ? hv : t;
        rrect(g, tx, ty, tile, tile, c(lerpColor(Theme.borderSoft(), Theme.accentSoft(), u)), c(lerpColor(Theme.tile(), Theme.accentTile(), u)));
        int iconWidth = Icons.bitmapWidth(item.icon);
        Icons.bitmap(g, tx + (tile - iconWidth) / 2, ty + (tile - item.icon.length) / 2, item.icon,
                c(lerpColor(Theme.tone(0.40f), Theme.accentPale(), u)));

        // name and description
        int textX = tx + tile + 10;
        int lineStep = this.font.lineHeight + 1;
        int block = this.font.lineHeight + 3 + item.lines.size() * lineStep - 1;
        if (item.detail != null) block += this.font.lineHeight + 3;
        int textY = iy + (h - block) / 2;
        String title = title(item);
        text(g, title, textX, textY, lerpColor(Theme.text(), WHITE, Math.max(hv, t)));
        if (master) {
            drawStatusPill(g, textX + this.font.width(title) + 8, textY - 2, t);
        }
        for (int i = 0; i < item.lines.size(); i++) {
            text(g, item.lines.get(i), textX, textY + this.font.lineHeight + 3 + i * lineStep, Theme.muted());
        }
        if (item.detail != null) {
            int detailWidth = innerWidth - (10 + tile + 10) - 30 - 22;
            text(g, UiText.fitStart(this.font, item.detail, detailWidth), textX,
                    textY + this.font.lineHeight + 3 + item.lines.size() * lineStep + 2,
                    lerpColor(Theme.tone(0.40f), Theme.accentLight(), hv));
        }

        if (link) {
            drawTrailing(g, item, ix + w - 12, iy + h / 2, hv);
            return;
        }

        // toggle
        int sw = master ? MASTER_SWITCH_W : ROW_SWITCH_W;
        int sh = master ? MASTER_SWITCH_H : ROW_SWITCH_H;
        drawSwitch(g, ix + w - 12 - sw, iy + (h - sh) / 2, sw, sh, t, hv);
    }

    private void drawTrailing(GuiGraphicsExtractor g, Item item, int rightX, int cy, float hv) {
        int idle = Theme.tone(0.42f);
        int color = c(lerpColor(idle, Theme.accentLight(), hv));
        int nudge = Math.round(hv * 2f); // icon nudges right on hover
        if (item.trailing == Trailing.FOLDER) {
            int iw = Icons.bitmapWidth(SettingIcons.FOLDER);
            Icons.bitmap(g, rightX - iw, cy - SettingIcons.FOLDER.length / 2, SettingIcons.FOLDER, color);
            return;
        }
        // theme: only chevron ">"
        Icons.chevron(g, rightX - Icons.CHEVRON_WIDTH + nudge, cy - Icons.CHEVRON_HEIGHT / 2, false, color);
    }

    private void drawSection(GuiGraphicsExtractor g, Item item, int ix, int iy) {
        g.fill(ix, iy + 3, ix + 2, iy + item.h - 3, c(Theme.accent()));
        String label = title(item);
        int ty = iy + (item.h - this.font.lineHeight) / 2 + 1;
        text(g, label, ix + 8, ty, Theme.accentLight());
        g.fill(ix + 8 + this.font.width(label) + 8, iy + item.h / 2, ix + innerWidth, iy + item.h / 2 + 1,
                c(Theme.borderSoft()));
    }

    private void drawStatusPill(GuiGraphicsExtractor g, int x, int y, float t) {
        String label = Component.translatable(t > 0.5f ? "quickactions.settings.on" : "quickactions.settings.off")
                .getString();
        int w = this.font.width(label) + 10;
        int h = this.font.lineHeight + 4;
        rrect(g, x, y, w, h, c(lerpColor(Theme.borderStrong(), 0xFF2E8B57, t)), c(lerpColor(Theme.cardHover(), 0xFF1F5F3A, t)));
        text(g, label, x + 5, y + 3, lerpColor(Theme.muted(), 0xFF7DFF9F, t));
    }

    private void drawSwitch(GuiGraphicsExtractor g, int x, int y, int w, int h, float t, float hv) {
        int r = h / 2;
        int borderOff = lerpColor(Theme.borderStrong(), Theme.tone(0.36f), hv);
        int borderOn = lerpColor(Colors.mix(Theme.accent(), 0xFFFFFFFF, 0.12f), Theme.accentPale(), hv);
        pill(g, x - 1, y - 1, w + 2, h + 2, c(lerpColor(borderOff, borderOn, t)));
        pill(g, x, y, w, h, c(lerpColor(Theme.borderSoft(), Colors.mix(Theme.accent(), 0xFF000000, 0.12f), t)));

        int cx = x + r + Math.round(t * (w - h));
        int cy = y + r;
        Icons.fillCircle(g, cx, cy + 1, r - 2, withAlpha(0x000000, 0.35f * drawAppear));
        Icons.fillCircle(g, cx, cy, r - 2, c(lerpColor(Theme.textSoft(), WHITE, t)));
    }

    private static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int r = h / 2;
        double radius = r + 0.5;
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            g.fill(x + r - half, y + r + dy, x + w - r + half, y + r + dy + 1, color);
        }
    }

    private static void rrect(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
        g.fill(x + 1, y, x + w - 1, y + 1, border);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        int shown = c(color);
        if ((shown >>> 24) < 8) return;
        g.text(this.font, Component.literal(text), x, y, shown, true);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int mouseX, int mouseY, float dt) {
        int[] geo = scrollbarGeometry();
        if (geo == null) {
            barHover = 0f;
            return;
        }
        boolean hot = barDragging || overScrollbar(mouseX, mouseY);
        barHover += ((hot ? 1f : 0f) - barHover) * Math.min(1f, dt * 16f);
        int left = x - Math.round(barHover * 2f);
        int right = x + 3;
        g.fill(left, geo[0], right, geo[0] + geo[1], withAlpha(0xFFFFFF, 0.13f + 0.06f * barHover));
        g.fill(left, geo[3], right, geo[3] + geo[2],
                barDragging ? Theme.accent() : lerpColor(Theme.scroll(), Theme.tone(0.48f), barHover));
    }

    // ---------- colors ----------

    private int c(int argb) {
        int base = drawDim >= 0.999f || (argb >>> 24) != 0xFF ? argb : lerpColor(Theme.panelOpaque(), argb, drawDim);
        return scaleAlpha(base, drawAppear);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float ease(float t) {
        float k = 1f - clamp01(t);
        return 1f - k * k * k;
    }

    private static int scaleAlpha(int argb, float k) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(k));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        float k = clamp01(t);
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - k) + ((to >>> 24) & 0xFF) * k);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int gr = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** RGB + alpha 0..1 -> ARGB */
    private static int withAlpha(int rgb, float alpha) {
        return (Math.round(clamp01(alpha) * 255f) << 24) | (rgb & 0x00FFFFFF);
    }
}
