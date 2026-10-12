package com.quickactions.ui.settings;

import com.mojang.blaze3d.platform.InputConstants;
import com.quickactions.config.QuickActionsConfig;
import com.quickactions.ui.Clip;
import com.quickactions.ui.Cursors;
import com.quickactions.ui.Icons;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.ui.UiText;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.theme.Colors;
import com.quickactions.ui.theme.Theme;
import com.quickactions.ui.theme.Theme.Palette;
import com.quickactions.ui.theme.ThemePresets;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ThemeScreen extends Screen {
    // ---------- layout ----------
    private static final int PANEL_TOP = 40;
    private static final int FOOTER = 40;
    private static final int PANEL_MAX_WIDTH = 348;
    private static final int PAD = 8;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_HIT = 10;
    private static final int WHEEL_STEP = 46;
    private static final int SECTION_GAP = 12;
    private static final int HEADER_HEIGHT = 18;
    private static final int GAP = 6;
    private static final int PREVIEW_HEIGHT = 96;
    private static final int CHIP_HEIGHT = 30;
    private static final int TAB_HEIGHT = 28;
    private static final int PICKER_HEIGHT = 72;
    private static final int HUE_WIDTH = 14;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CELL = 4;
    private static final int TRASH_ZONE = 22;
    private static final long ANIM_MS = 320;
    private static final long DELETE_ARM_MS = 2500;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DANGER = 0xFFFF7B7B;
    private static final int DANGER_BORDER = 0xFF8A3A3A;

    private enum Kind {PRESET, USER, SAVE_NEW, TAB, SV, HUE, HEX, RANDOM, RESET}

    private record Zone(Kind kind, int index, int x, int y, int w, int h) {
    }

    private record Header(String key, int y) {
    }

    private final Screen parent;
    private final long openedMillis = System.currentTimeMillis();

    private final List<Zone> zones = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private final List<Palette> builtInPalettes = new ArrayList<>();
    private List<Palette> userPalettes = new ArrayList<>();
    private float[] hover = new float[0];
    private boolean layoutDirty = true;

    private int panelLeft;
    private int panelWidth;
    private int panelBottom;
    private int innerWidth;
    private int contentHeight;
    private int previewY;

    private float scroll;
    private float scrollTarget;
    private long lastNanos;
    private boolean barDragging;
    private int barGrab;
    private float barHover;

    // ---------- color state ----------
    private int surfaceRgb;
    private int accentRgb;
    private float[] hsvSurface;
    private float[] hsvAccent;
    private int target = 0;
    private Kind dragging;

    private boolean animating;
    private long animStart;
    private int animFromSurface;
    private int animFromAccent;

    private ThemePresets.User armedDelete;
    private long armedUntil;

    public ThemeScreen(Screen parent) {
        super(Component.translatable("quickactions.theme.title"));
        this.parent = parent;
        Palette now = Theme.get();
        surfaceRgb = now.surface;
        accentRgb = now.accent;
        syncHsv();
        for (ThemePresets.BuiltIn preset : ThemePresets.BUILT_IN) {
            builtInPalettes.add(Palette.of(preset.surface(), preset.accent()));
        }
    }

    private void syncHsv() {
        hsvSurface = Colors.toHsv(surfaceRgb);
        hsvAccent = Colors.toHsv(accentRgb);
    }

    // ---------- init ----------

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 16);
        panelLeft = (this.width - panelWidth) / 2;
        panelBottom = this.height - FOOTER;
        innerWidth = panelWidth - PAD * 2 - SCROLLBAR_WIDTH;
        barDragging = false;
        dragging = null;
        layoutDirty = true;

        int buttonWidth = Math.min(150, this.width - 24);
        addRenderableWidget(Button.builder(Component.translatable("quickactions.theme.button.done"),
                b -> onClose()).bounds((this.width - buttonWidth) / 2, this.height - 28, buttonWidth, 20).build());

        ScreenMouseEvents.allowMouseClick(this).register(this::onClick);
        ScreenMouseEvents.allowMouseRelease(this).register(this::onRelease);
        ScreenMouseEvents.allowMouseScroll(this).register(this::onScroll);
        ScreenKeyboardEvents.allowKeyPress(this).register(this::onKey);
    }

    @Override
    public void onClose() {
        finishAnimation();
        QuickActionsConfig.save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    // ---------- layout ----------

    private int addHeader(String key, int y) {
        headers.add(new Header(key, y));
        return y + HEADER_HEIGHT + 4;
    }

    private void layout() {
        layoutDirty = false;
        zones.clear();
        headers.clear();
        userPalettes = new ArrayList<>();
        for (ThemePresets.User user : ThemePresets.user()) {
            userPalettes.add(Palette.of(user.surface, user.accent));
        }

        int y = PAD;
        y = addHeader("section.preview", y);
        previewY = y;
        y += PREVIEW_HEIGHT + SECTION_GAP;

        int cols = innerWidth >= 250 ? 3 : 2;
        int chipW = (innerWidth - (cols - 1) * GAP) / cols;

        y = addHeader("section.presets", y);
        int count = ThemePresets.BUILT_IN.size();
        for (int i = 0; i < count; i++) {
            zones.add(new Zone(Kind.PRESET, i, (i % cols) * (chipW + GAP), y + (i / cols) * (CHIP_HEIGHT + GAP),
                    chipW, CHIP_HEIGHT));
        }
        y += ((count + cols - 1) / cols) * (CHIP_HEIGHT + GAP) - GAP + SECTION_GAP;

        y = addHeader("section.custom", y);
        int tabW = (innerWidth - GAP) / 2;
        zones.add(new Zone(Kind.TAB, 0, 0, y, tabW, TAB_HEIGHT));
        zones.add(new Zone(Kind.TAB, 1, tabW + GAP, y, innerWidth - tabW - GAP, TAB_HEIGHT));
        y += TAB_HEIGHT + GAP;

        int columnW = 70;
        int svW = Math.max(96, Math.min(220, innerWidth - HUE_WIDTH - 8 - columnW - 8));
        int hueX = svW + 8;
        int columnX = hueX + HUE_WIDTH + 8;
        columnW = Math.max(40, innerWidth - columnX);
        zones.add(new Zone(Kind.SV, 0, 0, y, svW, PICKER_HEIGHT));
        zones.add(new Zone(Kind.HUE, 0, hueX, y, HUE_WIDTH, PICKER_HEIGHT));
        int step = (PICKER_HEIGHT - BUTTON_HEIGHT) / 2;
        zones.add(new Zone(Kind.HEX, 0, columnX, y, columnW, BUTTON_HEIGHT));
        zones.add(new Zone(Kind.RANDOM, 0, columnX, y + step, columnW, BUTTON_HEIGHT));
        zones.add(new Zone(Kind.RESET, 0, columnX, y + 2 * step, columnW, BUTTON_HEIGHT));
        y += PICKER_HEIGHT + SECTION_GAP;

        y = addHeader("section.mine", y);
        int users = userPalettes.size();
        if (users == 0) {
            zones.add(new Zone(Kind.SAVE_NEW, 0, 0, y, innerWidth, CHIP_HEIGHT + 8));
            y += CHIP_HEIGHT + 8;
        } else {
            int userCols = innerWidth >= 250 ? 2 : 1;
            int userW = (innerWidth - (userCols - 1) * GAP) / userCols;
            int cells = users + 1; // first cell: "save current"
            for (int i = 0; i < cells; i++) {
                int cx = (i % userCols) * (userW + GAP);
                int cy = y + (i / userCols) * (CHIP_HEIGHT + GAP);
                zones.add(i == 0 ? new Zone(Kind.SAVE_NEW, 0, cx, cy, userW, CHIP_HEIGHT)
                        : new Zone(Kind.USER, i - 1, cx, cy, userW, CHIP_HEIGHT));
            }
            y += ((cells + userCols - 1) / userCols) * (CHIP_HEIGHT + GAP) - GAP;
        }
        contentHeight = y + PAD;
        if (hover.length != zones.size()) hover = new float[zones.size()];
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

    // ---------- color work ----------

    private float[] hsv() {
        return target == 0 ? hsvAccent : hsvSurface;
    }

    private float vMin() {
        return target == 0 ? Theme.ACCENT_V_MIN : Theme.SURFACE_V_MIN;
    }

    private float vMax() {
        return target == 0 ? 1f : Theme.SURFACE_V_MAX;
    }

    private void applyHsv() {
        if (target == 0) {
            accentRgb = 0xFF000000 | Theme.normalizeAccent(Colors.fromHsv(hsvAccent[0], hsvAccent[1], hsvAccent[2]));
        } else {
            surfaceRgb = 0xFF000000 | Theme.normalizeSurface(Colors.fromHsv(hsvSurface[0], hsvSurface[1], hsvSurface[2]));
        }
        animating = false;
        Theme.set(surfaceRgb, accentRgb);
    }

    private void animateTo(int toSurface, int toAccent) {
        Palette live = Theme.get();
        animFromSurface = live.surface;
        animFromAccent = live.accent;
        surfaceRgb = 0xFF000000 | Theme.normalizeSurface(toSurface);
        accentRgb = 0xFF000000 | Theme.normalizeAccent(toAccent);
        syncHsv();
        animStart = System.currentTimeMillis();
        animating = true;
        Theme.saveToConfig(surfaceRgb, accentRgb);
    }

    private void finishAnimation() {
        if (animating || dragging != null) {
            animating = false;
            dragging = null;
            Theme.set(surfaceRgb, accentRgb);
        }
        Theme.saveToConfig(surfaceRgb, accentRgb);
    }

    private void tickAnimation() {
        if (!animating) return;
        float t = clamp01((System.currentTimeMillis() - animStart) / (float) ANIM_MS);
        if (t >= 1f) {
            animating = false;
            Theme.set(surfaceRgb, accentRgb);
            return;
        }
        float e = ease(t);
        Theme.set(Colors.mix(animFromSurface, surfaceRgb, e), Colors.mix(animFromAccent, accentRgb, e));
    }

    private boolean isCurrent(int surface, int accent) {
        return ThemePresets.sameColors(surface, accent, surfaceRgb, accentRgb);
    }

    // ---------- input ----------

    private int contentX() {
        return panelLeft + PAD;
    }

    private int zoneTop(Zone z) {
        return PANEL_TOP + z.y() - Math.round(scroll);
    }

    private int zoneAt(double mx, double my) {
        if (mx < contentX() || mx >= contentX() + innerWidth || my < PANEL_TOP || my >= panelBottom) return -1;
        double cx = mx - contentX();
        double cy = my - PANEL_TOP + Math.round(scroll);
        for (int i = 0; i < zones.size(); i++) {
            Zone z = zones.get(i);
            if (cx >= z.x() && cx < z.x() + z.w() && cy >= z.y() && cy < z.y() + z.h()) return i;
        }
        return -1;
    }

    private Zone zoneOfKind(Kind kind) {
        for (Zone z : zones) {
            if (z.kind() == kind) return z;
        }
        return null;
    }

    private boolean onClick(Screen screen, MouseButtonEvent event) {
        if (event.button() != MouseButtons.LEFT) return true;
        double mx = event.x();
        double my = event.y();
        if (overScrollbar(mx, my)) {
            beginBarDrag(my);
            return false;
        }
        int index = zoneAt(mx, my);
        if (index < 0) return true;
        Zone z = zones.get(index);
        double localX = mx - contentX() - z.x();

        switch (z.kind()) {
            case PRESET -> {
                disarmDelete();
                ThemePresets.BuiltIn preset = ThemePresets.BUILT_IN.get(z.index());
                animateTo(preset.surface(), preset.accent());
            }
            case USER -> {
                ThemePresets.User user = ThemePresets.user().get(z.index());
                if (localX >= z.w() - TRASH_ZONE) {
                    onTrashClicked(user);
                } else {
                    disarmDelete();
                    animateTo(user.surface, user.accent);
                }
            }
            case SAVE_NEW -> {
                disarmDelete();
                onSaveNew();
            }
            case TAB -> target = z.index();
            case SV, HUE -> {
                dragging = z.kind();
                disarmDelete();
                updateDrag(mx, my);
            }
            case HEX -> openHexInput();
            case RANDOM -> {
                int[] pair = ThemePresets.random();
                animateTo(pair[0], pair[1]);
            }
            case RESET -> animateTo(Theme.DEFAULT_SURFACE, Theme.DEFAULT_ACCENT);
        }
        return false;
    }

    private boolean onRelease(Screen screen, MouseButtonEvent event) {
        if (event.button() == MouseButtons.LEFT) {
            barDragging = false;
            if (dragging != null) {
                dragging = null;
                Theme.saveToConfig(surfaceRgb, accentRgb);
            }
        }
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

    private void updateDrag(double mx, double my) {
        if (dragging == null) return;
        Zone z = zoneOfKind(dragging);
        if (z == null) return;
        float[] hsv = hsv();
        if (dragging == Kind.SV) {
            hsv[1] = clamp01((float) ((mx - contentX() - z.x()) / z.w()));
            float fy = clamp01((float) ((my - zoneTop(z)) / z.h()));
            hsv[2] = vMax() - fy * (vMax() - vMin());
        } else {
            hsv[0] = Math.min(0.9999f, clamp01((float) ((my - zoneTop(z)) / z.h())));
        }
        applyHsv();
    }

    private void onTrashClicked(ThemePresets.User user) {
        long now = System.currentTimeMillis();
        if (armedDelete == user && now < armedUntil) {
            armedDelete = null;
            ThemePresets.deleteUser(user);
            layoutDirty = true;
            return;
        }
        armedDelete = user; // first click only arms, second deletes
        armedUntil = now + DELETE_ARM_MS;
    }

    private void disarmDelete() {
        armedDelete = null;
    }

    private void onSaveNew() {
        if (ThemePresets.user().size() >= ThemePresets.MAX_USER_PRESETS) {
            Notification.error(Component.translatable("quickactions.theme.limit.title"))
                    .message(Component.translatable("quickactions.theme.limit.message", ThemePresets.MAX_USER_PRESETS))
                    .show();
            return;
        }
        finishAnimation();
        int index = ThemePresets.user().size() + 1;
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                this,
                Component.translatable("quickactions.theme.save.title"),
                Component.translatable("quickactions.theme.save.default_name", index).getString(),
                ThemePresets.NAME_MAX_LENGTH,
                name -> {
                    if (ThemePresets.saveUser(name, surfaceRgb, accentRgb)) {
                        layoutDirty = true;
                        Notification.success(Component.translatable("quickactions.theme.saved.title"))
                                .message(Component.literal(name))
                                .show();
                    }
                }));
    }

    private void openHexInput() {
        finishAnimation();
        int current = (target == 0 ? accentRgb : surfaceRgb) & 0xFFFFFF;
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                this,
                Component.translatable(target == 0 ? "quickactions.theme.hex.title_accent" : "quickactions.theme.hex.title_surface"),
                Colors.hex(current),
                7,
                Component.translatable("quickactions.theme.hex.hint"),
                false,
                text -> {
                    int parsed = Colors.parseHex(text);
                    if (parsed < 0) {
                        Notification.error(Component.translatable("quickactions.theme.hex.invalid")).show();
                        return;
                    }
                    if (target == 0) {
                        animateTo(surfaceRgb, parsed);
                    } else {
                        animateTo(parsed, accentRgb);
                    }
                }));
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
        float t = clamp01((mouseY - barGrab - geo[0]) / travel);
        scrollTarget = t * max;
        scroll = scrollTarget;
    }

    // ---------- render ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (layoutDirty) layout();
        tickAnimation();
        super.extractRenderState(g, mouseX, mouseY, delta);

        long nowNanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min((nowNanos - lastNanos) / 1_000_000_000f, 0.1f);
        lastNanos = nowNanos;
        long elapsed = System.currentTimeMillis() - openedMillis;

        if (armedDelete != null && System.currentTimeMillis() > armedUntil) armedDelete = null;

        updateBarDrag(mouseY);
        updateDrag(mouseX, mouseY);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 18f));
        if (Math.abs(scrollTarget - scroll) < 0.3f) scroll = scrollTarget;

        int hovered = barDragging || dragging != null || overScrollbar(mouseX, mouseY) ? -1 : zoneAt(mouseX, mouseY);
        float follow = Math.min(1f, dt * 14f);
        for (int i = 0; i < hover.length; i++) {
            hover[i] += ((i == hovered ? 1f : 0f) - hover[i]) * follow;
        }

        drawTitle(g, dt);
        drawPanel(g, mouseX, mouseY, dt, elapsed);
        if (hovered >= 0 || dragging != null) Cursors.hand(g);
    }

    private void drawTitle(GuiGraphicsExtractor g, float dt) {
        int titleWidth = this.font.width(this.title);
        int iconW = Icons.bitmapWidth(SettingIcons.PALETTE);
        int x = (this.width - (iconW + 8 + titleWidth)) / 2;
        Icons.bitmap(g, x, 12, SettingIcons.PALETTE, Theme.accentLight());
        g.text(this.font, this.title, x + iconW + 8, 12, WHITE, true);
        Component subtitle = Component.translatable("quickactions.theme.subtitle");
        g.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2,
                12 + this.font.lineHeight + 3, Theme.muted(), true);
    }

    private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt, long elapsed) {
        int right = panelLeft + panelWidth;
        float open = ease(clamp01(elapsed / 220f));

        g.fill(panelLeft - 1, PANEL_TOP - 1, right + 1, panelBottom + 1, scaleAlpha(Theme.border(), open));
        g.fill(panelLeft, PANEL_TOP, right, panelBottom, scaleAlpha(Theme.panelBg(), open));

        int offset = Math.round(scroll);
        int ix = contentX();
        Clip.begin(g, panelLeft, PANEL_TOP, right, panelBottom);

        for (Header header : headers) {
            int hy = PANEL_TOP + header.y() - offset;
            if (hy + HEADER_HEIGHT < PANEL_TOP || hy > panelBottom) continue;
            drawSection(g, header.key(), ix, hy);
        }
        int py = PANEL_TOP + previewY - offset;
        if (py + PREVIEW_HEIGHT >= PANEL_TOP && py <= panelBottom) {
            drawPreview(g, ix, py, innerWidth, PREVIEW_HEIGHT);
        }
        for (int i = 0; i < zones.size(); i++) {
            Zone z = zones.get(i);
            int zy = PANEL_TOP + z.y() - offset;
            if (zy + z.h() < PANEL_TOP || zy > panelBottom) continue;
            float hv = i < hover.length ? hover[i] : 0f;
            switch (z.kind()) {
                case PRESET -> drawChip(g, builtInPalettes.get(z.index()), presetName(z.index()),
                        ix + z.x(), zy, z.w(), z.h(), hv,
                        isCurrent(ThemePresets.BUILT_IN.get(z.index()).surface(), ThemePresets.BUILT_IN.get(z.index()).accent()),
                        false, false);
                case USER -> {
                    ThemePresets.User user = ThemePresets.user().get(z.index());
                    boolean armed = armedDelete == user;
                    drawChip(g, userPalettes.get(z.index()), user.name, ix + z.x(), zy, z.w(), z.h(), hv,
                            isCurrent(user.surface, user.accent), true, armed);
                }
                case SAVE_NEW -> drawSaveCard(g, z, ix + z.x(), zy, hv);
                case TAB -> drawTab(g, z, ix + z.x(), zy, hv);
                case SV -> drawSaturationValue(g, z, ix + z.x(), zy);
                case HUE -> drawHue(g, z, ix + z.x(), zy);
                case HEX -> drawHexButton(g, ix + z.x(), zy, z.w(), z.h(), hv);
                case RANDOM -> drawButton(g, ix + z.x(), zy, z.w(), z.h(), hv, SettingIcons.DICE,
                        Component.translatable("quickactions.theme.button.random").getString());
                case RESET -> drawButton(g, ix + z.x(), zy, z.w(), z.h(), hv, SettingIcons.UNDO,
                        Component.translatable("quickactions.theme.button.reset").getString());
            }
        }

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

    private void drawSection(GuiGraphicsExtractor g, String key, int ix, int iy) {
        g.fill(ix, iy + 3, ix + 2, iy + HEADER_HEIGHT - 3, Theme.accent());
        String label = Component.translatable("quickactions.theme." + key).getString();
        int ty = iy + (HEADER_HEIGHT - this.font.lineHeight) / 2 + 1;
        text(g, label, ix + 8, ty, Theme.accentLight());
        g.fill(ix + 8 + this.font.width(label) + 8, iy + HEADER_HEIGHT / 2, ix + innerWidth,
                iy + HEADER_HEIGHT / 2 + 1, Theme.borderSoft());
    }

    private static String presetName(int index) {
        return Component.translatable("quickactions.theme.preset." + ThemePresets.BUILT_IN.get(index).id()).getString();
    }

    // ---------- preset chips ----------

    private void drawChip(GuiGraphicsExtractor g, Palette p, String name, int x, int y, int w, int h, float hv,
                          boolean selected, boolean deletable, boolean armed) {
        int border = armed ? DANGER_BORDER
                : selected ? Theme.accent() : Colors.mix(p.border, p.accent, hv);
        int fill = Colors.mix(p.panelOpaque, p.cardHover, 0.5f + 0.5f * hv);
        rrect(g, x, y, w, h, border, fill);
        // preset stripe and accent dot
        g.fill(x + 1, y + 4, x + 3, y + h - 4, p.accent);
        int cy = y + h / 2;
        Icons.fillCircle(g, x + 15, cy, 6, p.border);
        Icons.fillCircle(g, x + 15, cy, 5, p.accent);

        int textLeft = x + 26;
        int textRight = x + w - 6 - (deletable ? TRASH_ZONE - 4 : 0);
        String shown = armed ? Component.translatable("quickactions.theme.delete_confirm").getString() : name;
        shown = UiText.fitEnd(this.font, shown, Math.max(10, textRight - textLeft - (selected ? 12 : 0)));
        text(g, shown, textLeft, cy - this.font.lineHeight / 2 + 1,
                armed ? DANGER : Colors.mix(p.text, WHITE, hv));
        if (selected && !armed) {
            Icons.checkMark(g, textLeft + this.font.width(shown) + 9, cy, p.accentLight);
        }
        if (deletable) {
            int iconW = Icons.bitmapWidth(SettingIcons.TRASH);
            int tx = x + w - 8 - iconW;
            int ty = cy - SettingIcons.TRASH.length / 2;
            int color = armed ? DANGER : Colors.mixArgb(withAlpha(p.muted & 0xFFFFFF, 0.35f), p.muted, hv);
            Icons.bitmap(g, tx, ty, SettingIcons.TRASH, color);
        }
    }

    private void drawSaveCard(GuiGraphicsExtractor g, Zone z, int x, int y, float hv) {
        int w = z.w();
        int h = z.h();
        int border = Colors.mix(Theme.borderStrong(), Theme.accent(), hv);
        rrect(g, x, y, w, h, border, Colors.mix(Theme.card(), Theme.cardHover(), hv));
        int cy = y + h / 2;
        int iconW = Icons.bitmapWidth(SettingIcons.PLUS);
        boolean wide = w > 200;
        int iconColor = Colors.mix(Theme.tone(0.5f), Theme.accentPale(), hv);
        Component label = Component.translatable("quickactions.theme.save_new");
        if (wide) {
            Icons.bitmap(g, x + 12, cy - SettingIcons.PLUS.length / 2, SettingIcons.PLUS, iconColor);
            text(g, label.getString(), x + 12 + iconW + 8, cy - this.font.lineHeight - 1,
                    Colors.mix(Theme.text(), WHITE, hv));
            text(g, UiText.fitEnd(this.font, Component.translatable("quickactions.theme.save_new.desc").getString(),
                    w - 12 - iconW - 8 - 10), x + 12 + iconW + 8, cy + 2, Theme.muted());
        } else {
            Icons.bitmap(g, x + 8, cy - SettingIcons.PLUS.length / 2, SettingIcons.PLUS, iconColor);
            text(g, UiText.fitEnd(this.font, label.getString(), w - 8 - iconW - 8 - 6), x + 8 + iconW + 6,
                    cy - this.font.lineHeight / 2 + 1, Colors.mix(Theme.text(), WHITE, hv));
        }
    }

    // ---------- custom color ----------

    private void drawTab(GuiGraphicsExtractor g, Zone z, int x, int y, float hv) {
        boolean selected = target == z.index();
        int w = z.w();
        int h = z.h();
        int border = selected ? Theme.accent() : Colors.mix(Theme.borderSoft(), Theme.accentSoft(), hv);
        rrect(g, x, y, w, h, border, Colors.mix(Theme.card(), Theme.cardHover(), selected ? 1f : hv));
        if (selected) g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, Theme.accent());

        int rgb = (z.index() == 0 ? Theme.accent() : Theme.surface()) & 0xFFFFFF;
        int cy = y + h / 2;
        Icons.fillCircle(g, x + 15, cy, 7, selected ? WHITE : Theme.borderStrong());
        Icons.fillCircle(g, x + 15, cy, 6, 0xFF000000 | rgb);
        String label = Component.translatable(z.index() == 0 ? "quickactions.theme.tab.accent" : "quickactions.theme.tab.surface")
                .getString();
        text(g, label, x + 28, cy - this.font.lineHeight / 2 + 1, selected ? WHITE : Theme.text());
        String hex = Colors.hex(rgb);
        int hexWidth = this.font.width(hex);
        if (28 + this.font.width(label) + 8 + hexWidth + 8 < w) {
            text(g, hex, x + w - 8 - hexWidth, cy - this.font.lineHeight / 2 + 1, Theme.muted());
        }
    }

    private void drawSaturationValue(GuiGraphicsExtractor g, Zone z, int x, int y) {
        int w = z.w();
        int h = z.h();
        float[] hsv = hsv();
        float vMin = vMin();
        float vMax = vMax();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.borderStrong());
        int cols = (w + CELL - 1) / CELL;
        int rows = (h + CELL - 1) / CELL;
        for (int r = 0; r < rows; r++) {
            int y0 = y + r * CELL;
            int y1 = Math.min(y0 + CELL, y + h);
            if (y1 < PANEL_TOP || y0 > panelBottom) continue;
            float v = vMax - ((r + 0.5f) * CELL / h) * (vMax - vMin);
            for (int c = 0; c < cols; c++) {
                int x0 = x + c * CELL;
                int x1 = Math.min(x0 + CELL, x + w);
                float s = Math.min(1f, (c + 0.5f) * CELL / w);
                g.fill(x0, y0, x1, y1, Colors.fromHsv(hsv[0], s, v));
            }
        }
        // current color marker
        float fy = vMax - vMin <= 0f ? 0f : (vMax - hsv[2]) / (vMax - vMin);
        int mx = x + Math.round(clamp01(hsv[1]) * (w - 1));
        int my = y + Math.round(clamp01(fy) * (h - 1));
        Icons.ringCircle(g, mx, my, 6, 1, 0xB0000000);
        Icons.ringCircle(g, mx, my, 5, 2, WHITE);
    }

    private void drawHue(GuiGraphicsExtractor g, Zone z, int x, int y) {
        int w = z.w();
        int h = z.h();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.borderStrong());
        int rows = (h + CELL - 1) / CELL;
        for (int r = 0; r < rows; r++) {
            int y0 = y + r * CELL;
            int y1 = Math.min(y0 + CELL, y + h);
            if (y1 < PANEL_TOP || y0 > panelBottom) continue;
            g.fill(x, y0, x + w, y1, Colors.fromHsv((r + 0.5f) * CELL / h, 1f, 1f));
        }
        int my = y + Math.round(clamp01(hsv()[0]) * (h - 1));
        g.fill(x - 3, my - 2, x + w + 3, my + 3, 0xB0000000);
        g.fill(x - 2, my - 1, x + w + 2, my + 2, WHITE);
    }

    private void drawHexButton(GuiGraphicsExtractor g, int x, int y, int w, int h, float hv) {
        rrect(g, x, y, w, h, Colors.mix(Theme.border(), Theme.accent(), hv), Colors.mix(Theme.card(), Theme.accentTile(), hv));
        int rgb = (target == 0 ? accentRgb : surfaceRgb) & 0xFFFFFF;
        String hex = Colors.hex(rgb);
        int cy = y + h / 2;
        g.fill(x + 5, cy - 4, x + 13, cy + 4, Theme.borderStrong());
        g.fill(x + 6, cy - 3, x + 12, cy + 3, 0xFF000000 | rgb);
        text(g, UiText.fitEnd(this.font, hex, w - 20), x + 17, cy - this.font.lineHeight / 2 + 1,
                Colors.mix(Theme.text(), WHITE, hv));
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, float hv, String[] icon, String label) {
        rrect(g, x, y, w, h, Colors.mix(Theme.border(), Theme.accent(), hv), Colors.mix(Theme.card(), Theme.accentTile(), hv));
        int cy = y + h / 2;
        int iw = Icons.bitmapWidth(icon);
        Icons.bitmap(g, x + 6, cy - icon.length / 2, icon, Colors.mix(Theme.tone(0.5f), Theme.accentPale(), hv));
        text(g, UiText.fitEnd(this.font, label, w - 6 - iw - 6 - 4), x + 6 + iw + 5, cy - this.font.lineHeight / 2 + 1,
                Colors.mix(Theme.text(), WHITE, hv));
    }

    // ---------- preview ----------

    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        rrect(g, x, y, w, h, Theme.border(), Theme.deep());
        int pad = 6;
        int leftW = (int) ((w - pad * 3) * 0.56f);
        int rightW = w - pad * 3 - leftW;
        int cardH = (h - pad * 3) / 2;

        drawMiniCard(g, x + pad, y + pad, leftW, cardH, true, SettingIcons.WORLD,
                "quickactions.theme.preview.card_on");
        drawMiniCard(g, x + pad, y + pad * 2 + cardH, leftW, cardH, false, SettingIcons.SERVER,
                "quickactions.theme.preview.card_off");

        // mini menu
        int mx = x + pad * 2 + leftW;
        int my = y + pad;
        int mh = h - pad * 2;
        g.fill(mx - 1, my - 1, mx + rightW + 1, my + mh + 1, Theme.border());
        g.fill(mx, my, mx + rightW, my + mh, Theme.panelBg());
        int rowH = (mh - 8) / 3;
        String[] keys = {"quickactions.theme.preview.menu1", "quickactions.theme.preview.menu2", "quickactions.theme.preview.menu3"};
        for (int i = 0; i < 3; i++) {
            int ry = my + 4 + i * rowH;
            boolean hot = i == 1;
            if (hot) g.fill(mx + 2, ry, mx + rightW - 2, ry + rowH, Theme.accentDeep());
            // checkbox
            int bx = mx + 8;
            int by = ry + rowH / 2 - 4;
            g.fill(bx, by, bx + 9, by + 9, i == 0 ? Theme.accent() : Theme.subtle());
            g.fill(bx + 1, by + 1, bx + 8, by + 8, i == 0 ? Theme.accent() : Theme.deep());
            if (i == 0) Icons.checkMark(g, bx + 4, by + 4, WHITE);
            String label = UiText.fitEnd(this.font, Component.translatable(keys[i]).getString(), rightW - 8 - 9 - 8 - 6);
            text(g, label, bx + 14, ry + (rowH - this.font.lineHeight) / 2 + 1, WHITE);
        }
    }

    private void drawMiniCard(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on, String[] icon, String key) {
        rrect(g, x, y, w, h, on ? Theme.accentSoft() : Theme.borderSoft(), Theme.card());
        if (on) g.fill(x, y + 4, x + 2, y + h - 4, Theme.accent());
        int tile = Math.min(22, h - 8);
        int tx = x + 7;
        int ty = y + (h - tile) / 2;
        rrect(g, tx, ty, tile, tile, on ? Theme.accentSoft() : Theme.borderSoft(), on ? Theme.accentTile() : Theme.tile());
        int iw = Icons.bitmapWidth(icon);
        Icons.bitmap(g, tx + (tile - iw) / 2, ty + (tile - icon.length) / 2, icon, on ? Theme.accentPale() : Theme.tone(0.40f));

        int switchW = 23;
        int switchH = 11;
        int textX = tx + tile + 7;
        int textWidth = x + w - 7 - switchW - 6 - textX;
        String title = Component.translatable(key).getString();
        String desc = Component.translatable(key + ".desc").getString();
        int block = this.font.lineHeight * 2 + 2;
        int textY = y + (h - block) / 2;
        text(g, UiText.fitEnd(this.font, title, textWidth), textX, textY, on ? WHITE : Theme.text());
        text(g, UiText.fitEnd(this.font, desc, textWidth), textX, textY + this.font.lineHeight + 2, Theme.muted());
        drawMiniSwitch(g, x + w - 7 - switchW, y + (h - switchH) / 2, switchW, switchH, on);
    }

    private static void drawMiniSwitch(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on) {
        int r = h / 2;
        pill(g, x - 1, y - 1, w + 2, h + 2, on ? Colors.mix(Theme.accent(), WHITE, 0.12f) : Theme.borderStrong());
        pill(g, x, y, w, h, on ? Colors.mix(Theme.accent(), 0xFF000000, 0.12f) : Theme.borderSoft());
        int cx = x + r + (on ? w - h : 0);
        Icons.fillCircle(g, cx, y + r, r - 2, on ? WHITE : Theme.textSoft());
    }

    // ---------- scrollbar ----------

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
                barDragging ? Theme.accent() : Colors.mix(Theme.scroll(), Theme.tone(0.48f), barHover));
    }

    // ---------- drawing and colors ----------

    private void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        if ((color >>> 24) < 8) return;
        g.text(this.font, Component.literal(text), x, y, color, true);
    }

    /** pill of odd height h: one fill per row */
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

    /** RGB + alpha 0..1 -> ARGB */
    private static int withAlpha(int rgb, float alpha) {
        return (Math.round(clamp01(alpha) * 255f) << 24) | (rgb & 0x00FFFFFF);
    }
}
