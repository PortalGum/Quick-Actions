package com.quickactions.actions.pack.preset;

import com.quickactions.ui.theme.Theme;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.pack.PackListOperations;
import com.quickactions.actions.pack.preset.PackPreset.PresetPack;
import com.quickactions.ui.ChoiceScreen;
import com.quickactions.ui.HelpTip;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.ui.UiText;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import com.quickactions.util.ClientUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PresetsScreen extends Screen {
    private static final int CARD_HEIGHT = 46;
    private static final int CARD_GAP = 4;
    private static final int CARD_PITCH = CARD_HEIGHT + CARD_GAP;
    private static final int DETAIL_ROW = 22;
    private static final int DETAIL_HEADER = 56;
    private static final int ICON = 16;
    private static final int PANEL_TOP = 40;
    private static final int FOOTER = 62;
    private static final int LIST_WIDTH = 236;
    private static final int DETAIL_WIDTH = 252;
    private static final int PANEL_GAP = 8;
    private static final long DOUBLE_CLICK_MS = 350;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int ROW_ALT = 0x0DFFFFFF;
    private static final int PILL_ACTIVE_BG = 0xFF1F5F3A;
    private static final int PILL_ACTIVE_TEXT = 0xFF7DFF9F;
    private static final int PILL_MISSING_BG = 0xFF5F2323;
    private static final int PILL_MISSING_TEXT = 0xFFFF8A8A;
    private static final int MISSING_TILE = 0xFF2A1618;
    private static final int MISSING_BORDER = 0xFF8A3A3A;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Screen parent;

    private List<PackPreset> presets = List.of();
    private @Nullable Path selectedFile;
    private Map<String, Identifier> icons = Map.of();
    private List<String> currentIds = List.of();

    private int firstCard;
    private int firstDetail;
    private long lastClickMillis;
    private @Nullable Path lastClickFile;

    private int listLeft;
    private int listWidth;
    private int detailLeft;
    private int panelBottom;
    private boolean showDetail;

    private Button applyButton;
    private Button overwriteButton;
    private Button renameButton;
    private Button deleteButton;
    private Button saveButton;

    public PresetsScreen(Screen parent) {
        super(Component.translatable("quickactions.preset.title"));
        this.parent = parent;
    }

    // ---------- init ----------

    @Override
    protected void init() {
        icons = PackSetup.icons(parent);
        List<String> current = new ArrayList<>();
        for (PresetPack pack : PackSetup.captureCurrent(parent)) {
            current.add(pack.id());
        }
        currentIds = current;

        showDetail = this.width >= LIST_WIDTH + PANEL_GAP + DETAIL_WIDTH + 24;
        int group = showDetail ? LIST_WIDTH + PANEL_GAP + DETAIL_WIDTH : Math.min(this.width - 24, 360);
        int groupLeft = this.width / 2 - group / 2;
        listLeft = groupLeft;
        listWidth = showDetail ? LIST_WIDTH : group;
        detailLeft = groupLeft + LIST_WIDTH + PANEL_GAP;
        panelBottom = this.height - FOOTER;

        int bar = Math.min(this.width - 24, 500);
        int barLeft = this.width / 2 - bar / 2;
        int row1 = this.height - 52;
        int row2 = this.height - 28;

        int third = (bar - 8) / 3;
        applyButton = addButton("quickactions.preset.button.apply", this::applySelected, barLeft, row1, third);
        saveButton = addButton("quickactions.preset.button.save", this::saveCurrentAsNew, barLeft + third + 4, row1, third);
        overwriteButton = addButton("quickactions.preset.button.overwrite", this::overwriteSelected,
                barLeft + 2 * (third + 4), row1, third);

        int quarter = (bar - 12) / 4;
        renameButton = addButton("quickactions.preset.button.rename", this::renameSelected, barLeft, row2, quarter);
        deleteButton = addButton("quickactions.preset.button.delete", this::deleteSelected,
                barLeft + quarter + 4, row2, quarter);
        addButton("quickactions.preset.button.open_folder", this::openFolder,
                barLeft + 2 * (quarter + 4), row2, quarter);
        addButton("quickactions.preset.button.done", this::onClose, barLeft + 3 * (quarter + 4), row2, quarter);

        reload(null);

        ScreenMouseEvents.allowMouseClick(this).register(this::onClick);
        ScreenMouseEvents.allowMouseScroll(this).register(this::onScroll);
    }

    private Button addButton(String key, Runnable action, int x, int y, int width) {
        Button button = Button.builder(Component.translatable(key), b -> action.run())
                .bounds(x, y, width, 20).build();
        addRenderableWidget(button);
        return button;
    }

    // ---------- data ----------

    private void reload(@Nullable Path select) {
        presets = PresetStore.list();
        if (select != null) {
            selectedFile = select;
        }
        if (selectedFile != null && selectedPreset() == null) {
            selectedFile = null;
        }
        if (selectedFile == null && !presets.isEmpty()) {
            PackPreset pick = presets.get(0);
            for (PackPreset preset : presets) {
                if (isActive(preset)) {
                    pick = preset;
                    break;
                }
            }
            selectedFile = pick.file();
        }
        PackPreset selected = selectedPreset();
        if (selected != null) ensureVisible(selected);
        clampScroll();
        updateButtons();
    }

    private @Nullable PackPreset selectedPreset() {
        if (selectedFile == null) return null;
        for (PackPreset preset : presets) {
            if (preset.file().equals(selectedFile)) return preset;
        }
        return null;
    }

    private void select(PackPreset preset) {
        if (!preset.file().equals(selectedFile)) {
            firstDetail = 0;
        }
        selectedFile = preset.file();
        updateButtons();
    }

    private void updateButtons() {
        if (applyButton == null) return;
        boolean has = selectedPreset() != null;
        boolean hasCurrent = !currentIds.isEmpty(); // nothing to save without enabled packs
        applyButton.active = has;
        saveButton.active = hasCurrent;
        overwriteButton.active = has && hasCurrent;
        renameButton.active = has;
        deleteButton.active = has;
    }

    private boolean isActive(PackPreset preset) {
        return preset.ids().equals(currentIds);
    }

    private int missingCount(PackPreset preset) {
        int missing = 0;
        for (PresetPack pack : preset.packs()) {
            if (!icons.containsKey(pack.id())) missing++;
        }
        return missing;
    }

    // ---------- scroll and hit test ----------

    private int visibleCards() {
        return Math.max(1, (panelBottom - PANEL_TOP - 8 + CARD_GAP) / CARD_PITCH);
    }

    private int visibleDetailRows() {
        return Math.max(1, (panelBottom - (PANEL_TOP + DETAIL_HEADER) - 4) / DETAIL_ROW);
    }

    private void clampScroll() {
        firstCard = Math.max(0, Math.min(firstCard, Math.max(0, presets.size() - visibleCards())));
        PackPreset selected = selectedPreset();
        int packCount = selected == null ? 0 : selected.packs().size();
        firstDetail = Math.max(0, Math.min(firstDetail, Math.max(0, packCount - visibleDetailRows())));
    }

    private void ensureVisible(PackPreset preset) {
        int index = presets.indexOf(preset);
        if (index < 0) return;
        if (index < firstCard) {
            firstCard = index;
        } else if (index >= firstCard + visibleCards()) {
            firstCard = index - visibleCards() + 1;
        }
    }

    private boolean inList(double mx, double my) {
        return mx >= listLeft && mx < listLeft + listWidth && my >= PANEL_TOP && my < panelBottom;
    }

    private boolean inDetail(double mx, double my) {
        return showDetail && mx >= detailLeft && mx < detailLeft + DETAIL_WIDTH && my >= PANEL_TOP && my < panelBottom;
    }

    private int cardAt(double mx, double my) {
        if (mx < listLeft + 4 || mx >= listLeft + listWidth - 10 || my < PANEL_TOP + 4 || my >= panelBottom - 4) {
            return -1;
        }
        int relative = (int) (my - (PANEL_TOP + 4));
        if (relative % CARD_PITCH >= CARD_HEIGHT) return -1;
        int index = firstCard + relative / CARD_PITCH;
        if (index >= presets.size()) return -1;
        int cardBottom = PANEL_TOP + 4 + (index - firstCard) * CARD_PITCH + CARD_HEIGHT;
        return cardBottom <= panelBottom - 4 ? index : -1;
    }

    private boolean onClick(Screen screen, MouseButtonEvent event) {
        if (event.button() != MouseButtons.LEFT) return true;
        int index = cardAt(event.x(), event.y());
        if (index < 0) return true;

        PackPreset preset = presets.get(index);
        select(preset);

        long now = System.currentTimeMillis();
        if (preset.file().equals(lastClickFile) && now - lastClickMillis < DOUBLE_CLICK_MS) {
            lastClickFile = null;
            applySelected();
        } else {
            lastClickFile = preset.file();
            lastClickMillis = now;
        }
        return false;
    }

    private boolean onScroll(Screen screen, double mouseX, double mouseY, double horizontal, double vertical) {
        int step = (int) -Math.signum(vertical);
        if (inList(mouseX, mouseY)) {
            firstCard += step;
            clampScroll();
            return false;
        }
        if (inDetail(mouseX, mouseY)) {
            firstDetail += step;
            clampScroll();
            return false;
        }
        return true;
    }

    // ---------- actions ----------

    private void applySelected() {
        PackPreset preset = selectedPreset();
        if (preset == null) return;

        Minecraft.getInstance().setScreenAndShow(parent);
        PackSetup.ApplyResult result = PackSetup.apply(parent, preset);

        if (result.missing().isEmpty()) {
            Notification.success(Component.translatable("quickactions.notify.preset.applied.title"))
                    .message(Component.translatable("quickactions.notify.preset.applied.message", result.enabled()))
                    .detail(Component.translatable("quickactions.notify.pack.press_done").getString())
                    .show();
        } else {
            Notification.info(Component.translatable("quickactions.notify.preset.applied.title"))
                    .message(Component.translatable("quickactions.notify.preset.applied_missing.message",
                            result.enabled(), result.missing().size()))
                    .detail(String.join(", ", result.missing()))
                    .duration(10000)
                    .show();
        }
    }

    private void saveCurrentAsNew() {
        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                this,
                Component.translatable("quickactions.preset.save.title"),
                Component.translatable("quickactions.preset.default_name").getString(),
                48,
                name -> {
                    PackPreset created = PackListOperations.saveCurrent(parent, name);
                    if (created != null) reload(created.file());
                }));
    }

    private void overwriteSelected() {
        PackPreset preset = selectedPreset();
        if (preset == null) return;

        Minecraft.getInstance().setScreenAndShow(new ChoiceScreen(
                this,
                Component.translatable("quickactions.preset.overwrite.title"),
                Component.translatable("quickactions.preset.overwrite.message", preset.name(), currentIds.size()),
                List.of(
                        new ChoiceScreen.Choice(Component.translatable("quickactions.preset.overwrite.confirm"), () -> {
                            try {
                                PackPreset updated = PresetStore.overwrite(preset, PackSetup.captureCurrent(parent));
                                reload(updated.file());
                                Notification.success(Component.translatable("quickactions.notify.preset.updated.title"))
                                        .message(Component.literal(updated.name()))
                                        .show();
                            } catch (Exception e) {
                                QuickActionsClient.LOGGER.error("Failed to overwrite preset", e);
                                Notifications.failure();
                            }
                        }),
                        new ChoiceScreen.Choice(CommonComponents.GUI_CANCEL, () -> { }))));
    }

    private void renameSelected() {
        PackPreset preset = selectedPreset();
        if (preset == null) return;

        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                this,
                Component.translatable("quickactions.preset.rename.title"),
                preset.name(),
                48,
                newName -> {
                    try {
                        reload(PresetStore.rename(preset, newName).file());
                    } catch (Exception e) {
                        QuickActionsClient.LOGGER.error("Failed to rename preset", e);
                        Notifications.failure();
                    }
                }));
    }

    private void deleteSelected() {
        PackPreset preset = selectedPreset();
        if (preset == null) return;

        Minecraft.getInstance().setScreenAndShow(new ChoiceScreen(
                this,
                Component.translatable("quickactions.preset.delete.title"),
                Component.translatable("quickactions.preset.delete.message", preset.name()),
                List.of(
                        new ChoiceScreen.Choice(Component.translatable("quickactions.preset.delete.confirm"), () -> {
                            try {
                                PresetStore.delete(preset);
                                selectedFile = null;
                                reload(null);
                                Notification.success(Component.translatable("quickactions.notify.preset.deleted.title"))
                                        .message(Component.literal(preset.name()))
                                        .show();
                            } catch (Exception e) {
                                QuickActionsClient.LOGGER.error("Failed to delete preset", e);
                                Notifications.failure();
                            }
                        }),
                        new ChoiceScreen.Choice(CommonComponents.GUI_CANCEL, () -> { })),
                true));
    }

    private void openFolder() {
        Path dir = PresetStore.directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // open what's there
        }
        ClientUtil.openPath(dir);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    // ---------- render ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        g.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 12, WHITE, true);
        Component subtitle = Component.translatable("quickactions.preset.subtitle", currentIds.size());
        g.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2,
                12 + this.font.lineHeight + 3, Theme.muted(), true);

        drawListPanel(g, mouseX, mouseY);
        if (showDetail) {
            drawDetailPanel(g);
            drawPackTooltip(g, mouseX, mouseY);
        }
    }

    private void drawListPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int left = listLeft;
        int right = left + listWidth;
        g.fill(left - 1, PANEL_TOP - 1, right + 1, panelBottom + 1, Theme.border());
        g.fill(left, PANEL_TOP, right, panelBottom, Theme.panelBg());

        if (presets.isEmpty()) {
            Component title = Component.translatable("quickactions.preset.empty.title");
            Component hint = Component.translatable("quickactions.preset.empty.hint");
            int centerY = PANEL_TOP + (panelBottom - PANEL_TOP) / 2;
            g.text(this.font, title, left + (listWidth - this.font.width(title)) / 2,
                    centerY - this.font.lineHeight - 2, WHITE, true);
            String fitted = UiText.fitEnd(this.font, hint.getString(), listWidth - 16);
            g.text(this.font, Component.literal(fitted), left + (listWidth - this.font.width(fitted)) / 2,
                    centerY + 4, Theme.muted(), true);
            return;
        }

        int hovered = cardAt(mouseX, mouseY);
        int y = PANEL_TOP + 4;
        int visible = 0;
        for (int i = firstCard; i < presets.size(); i++) {
            if (y + CARD_HEIGHT > panelBottom - 4) break;
            PackPreset preset = presets.get(i);
            drawCard(g, preset, left + 4, y, listWidth - 14, preset.file().equals(selectedFile), i == hovered);
            y += CARD_PITCH;
            visible++;
        }
        drawScrollbar(g, right - 6, PANEL_TOP + 4, panelBottom - PANEL_TOP - 8, presets.size(), visible, firstCard);
    }

    private void drawCard(GuiGraphicsExtractor g, PackPreset preset, int x, int y, int w,
                          boolean selected, boolean hovered) {
        g.fill(x, y, x + w, y + CARD_HEIGHT, selected ? Theme.accentSelected() : hovered ? Theme.cardHover() : Theme.card());
        if (selected) {
            g.fill(x, y, x + w, y + 1, Theme.accent());
            g.fill(x, y + CARD_HEIGHT - 1, x + w, y + CARD_HEIGHT, Theme.accent());
            g.fill(x + w - 1, y, x + w, y + CARD_HEIGHT, Theme.accent());
        }
        int color = presetColor(preset.name());
        g.fillGradient(x, y, x + 3, y + CARD_HEIGHT, color, darken(color));

        boolean active = isActive(preset);
        int missing = missingCount(preset);
        String activeText = Component.translatable("quickactions.preset.badge.active").getString();
        String missingText = Component.translatable("quickactions.preset.badge.missing", missing).getString();
        int badges = 0;
        if (active) badges += this.font.width(activeText) + 14;
        if (missing > 0) badges += this.font.width(missingText) + 14;

        String name = UiText.fitEnd(this.font, preset.name(), w - 20 - badges);
        g.text(this.font, Component.literal(name), x + 10, y + 5, WHITE, true);

        int edge = x + w - 6;
        if (active) edge -= drawPill(g, edge, y + 4, activeText, PILL_ACTIVE_TEXT, PILL_ACTIVE_BG) + 4;
        if (missing > 0) drawPill(g, edge, y + 4, missingText, PILL_MISSING_TEXT, PILL_MISSING_BG);

        g.text(this.font, info(preset), x + 10, y + 5 + this.font.lineHeight + 2, Theme.muted(), true);
        drawIconStrip(g, preset, x + 10, y + CARD_HEIGHT - ICON - 4, w - 20);
    }

    private Component info(PackPreset preset) {
        return Component.translatable("quickactions.preset.card.info",
                preset.packs().size(), DATE_FORMAT.format(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(preset.savedMillis()), ZoneId.systemDefault())));
    }

    private void drawIconStrip(GuiGraphicsExtractor g, PackPreset preset, int x, int y, int width) {
        List<PresetPack> packs = preset.packs();
        if (packs.isEmpty()) {
            g.text(this.font, Component.translatable("quickactions.preset.vanilla"), x,
                    y + (ICON - this.font.lineHeight) / 2, Theme.muted(), true);
            return;
        }
        int slot = ICON + 2;
        int capacity = Math.max(1, width / slot);
        int shown = packs.size() <= capacity ? packs.size() : Math.max(1, capacity - 2);
        for (int i = 0; i < shown; i++) {
            drawIcon(g, packs.get(i).id(), x + i * slot, y);
        }
        if (packs.size() > shown) {
            g.text(this.font, Component.literal("+" + (packs.size() - shown)), x + shown * slot + 2,
                    y + (ICON - this.font.lineHeight) / 2, Theme.muted(), true);
        }
    }

    private void drawIcon(GuiGraphicsExtractor g, String id, int x, int y) {
        Identifier icon = icons.get(id);
        if (icon != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, ICON, ICON, ICON, ICON);
        } else {
            g.fill(x, y, x + ICON, y + ICON, MISSING_BORDER);
            g.fill(x + 1, y + 1, x + ICON - 1, y + ICON - 1, MISSING_TILE);
            g.text(this.font, Component.literal("?"), x + (ICON - this.font.width("?")) / 2,
                    y + (ICON - this.font.lineHeight) / 2, PILL_MISSING_TEXT, true);
        }
    }

    private int drawPill(GuiGraphicsExtractor g, int rightEdge, int y, String text, int textColor, int background) {
        int width = this.font.width(text) + 10;
        int x = rightEdge - width;
        g.fill(x, y, x + width, y + 12, background);
        g.text(this.font, Component.literal(text), x + 5, y + 2, textColor, true);
        return width;
    }

    private void drawDetailPanel(GuiGraphicsExtractor g) {
        int left = detailLeft;
        int right = left + DETAIL_WIDTH;
        g.fill(left - 1, PANEL_TOP - 1, right + 1, panelBottom + 1, Theme.border());
        g.fill(left, PANEL_TOP, right, panelBottom, Theme.panelBg());

        PackPreset preset = selectedPreset();
        if (preset == null) {
            Component title = Component.translatable("quickactions.preset.detail.empty_title");
            g.text(this.font, title, left + (DETAIL_WIDTH - this.font.width(title)) / 2,
                    PANEL_TOP + (panelBottom - PANEL_TOP) / 2 - this.font.lineHeight, WHITE, true);
            List<String> lines = UiText.wrap(this.font,
                    Component.translatable("quickactions.preset.detail.empty_hint").getString(), DETAIL_WIDTH - 32);
            int y = PANEL_TOP + (panelBottom - PANEL_TOP) / 2 + 4;
            for (String line : lines) {
                g.text(this.font, Component.literal(line), left + (DETAIL_WIDTH - this.font.width(line)) / 2,
                        y, Theme.muted(), true);
                y += this.font.lineHeight + 2;
            }
            return;
        }

        int color = presetColor(preset.name());
        g.fillGradient(left, PANEL_TOP, left + 3, panelBottom, color, darken(color));

        g.text(this.font, Component.literal(UiText.fitEnd(this.font, preset.name(), DETAIL_WIDTH - 28)),
                left + 12, PANEL_TOP + 8, WHITE, true);
        g.text(this.font, info(preset), left + 12, PANEL_TOP + 8 + this.font.lineHeight + 3, Theme.muted(), true);
        g.fill(left + 10, PANEL_TOP + 34, right - 10, PANEL_TOP + 35, Theme.border());
        String caption = UiText.fitEnd(this.font,
                Component.translatable("quickactions.preset.detail.hint").getString(), DETAIL_WIDTH - 28);
        g.text(this.font, Component.literal(caption), left + 12, PANEL_TOP + 40, Theme.muted(), true);

        List<PresetPack> packs = preset.packs();
        if (packs.isEmpty()) {
            g.text(this.font, Component.translatable("quickactions.preset.vanilla"), left + 12,
                    PANEL_TOP + DETAIL_HEADER + 4, Theme.muted(), true);
            return;
        }

        String missingText = Component.translatable("quickactions.preset.detail.missing").getString();
        int rowsTop = PANEL_TOP + DETAIL_HEADER;
        int rows = visibleDetailRows();
        int drawn = 0;
        for (int row = 0; row < rows && firstDetail + row < packs.size(); row++) {
            int index = firstDetail + row;
            PresetPack pack = packs.get(index);
            int y = rowsTop + row * DETAIL_ROW;
            boolean missing = !icons.containsKey(pack.id());

            if (row % 2 == 0) {
                g.fill(left + 6, y, right - 12, y + DETAIL_ROW, ROW_ALT);
            }
            g.text(this.font, Component.literal(String.valueOf(index + 1)), left + 12,
                    y + (DETAIL_ROW - this.font.lineHeight) / 2, Theme.muted(), true);
            drawIcon(g, pack.id(), left + 30, y + (DETAIL_ROW - ICON) / 2);

            int pill = 0;
            if (missing) {
                pill = drawPill(g, right - 16, y + (DETAIL_ROW - 12) / 2, missingText,
                        PILL_MISSING_TEXT, PILL_MISSING_BG) + 4;
            }
            String title = UiText.fitEnd(this.font, pack.title(), DETAIL_WIDTH - 54 - 16 - pill);
            g.text(this.font, Component.literal(title), left + 52, y + (DETAIL_ROW - this.font.lineHeight) / 2,
                    missing ? PILL_MISSING_TEXT : WHITE, true);
            drawn++;
        }
        drawScrollbar(g, right - 6, rowsTop, rows * DETAIL_ROW, packs.size(), drawn, firstDetail);
    }

    private void drawPackTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        PackPreset preset = selectedPreset();
        if (preset == null || !inDetail(mouseX, mouseY)) return;

        int rowsTop = PANEL_TOP + DETAIL_HEADER;
        if (mouseY < rowsTop || mouseX < detailLeft + 6 || mouseX >= detailLeft + DETAIL_WIDTH - 12) return;
        int row = (mouseY - rowsTop) / DETAIL_ROW;
        if (row >= visibleDetailRows()) return;

        List<PresetPack> packs = preset.packs();
        int index = firstDetail + row;
        if (index >= packs.size()) return;

        PresetPack pack = packs.get(index);
        boolean missing = !icons.containsKey(pack.id());

        // same name width as when drawing the row
        int pill = 0;
        if (missing) {
            String missingText = Component.translatable("quickactions.preset.detail.missing").getString();
            pill = this.font.width(missingText) + 10 + 4;
        }
        boolean truncated = this.font.width(pack.title()) > DETAIL_WIDTH - 54 - 16 - pill;
        if (!missing && !truncated) return;

        String text = pack.title();
        if (missing) {
            String file = pack.id().startsWith("file/") ? pack.id().substring("file/".length()) : pack.id();
            text += "\n" + Component.translatable("quickactions.preset.detail.missing_file", file).getString();
        }
        HelpTip.renderTooltip(g, this.font, Component.literal(text), mouseX, mouseY, this.width, this.height);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int top, int height, int total, int visible, int first) {
        if (total <= visible) return;
        int thumb = Math.max(12, height * visible / total);
        int thumbTop = top + (height - thumb) * first / Math.max(1, total - visible);
        g.fill(x, thumbTop, x + 3, thumbTop + thumb, Theme.scroll());
    }

    // ---------- colors ----------

    /** stable nice color by preset name */
    private static int presetColor(String name) {
        float hue = (name.hashCode() & 0x7FFFFFFF) % 360;
        return hsvToRgb(hue, 0.55f, 0.95f);
    }

    private static int darken(int argb) {
        int r = (int) (((argb >> 16) & 0xFF) * 0.4f);
        int g = (int) (((argb >> 8) & 0xFF) * 0.4f);
        int b = (int) ((argb & 0xFF) * 0.4f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float chroma = value * saturation;
        float sector = (hue / 60f) % 6;
        float x = chroma * (1 - Math.abs(sector % 2 - 1));
        float r = 0;
        float g = 0;
        float b = 0;
        if (sector < 1) { r = chroma; g = x; }
        else if (sector < 2) { r = x; g = chroma; }
        else if (sector < 3) { g = chroma; b = x; }
        else if (sector < 4) { g = x; b = chroma; }
        else if (sector < 5) { r = x; b = chroma; }
        else { r = chroma; b = x; }
        float m = value - chroma;
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}