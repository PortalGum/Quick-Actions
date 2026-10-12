package com.quickactions.integration;

import com.quickactions.ui.theme.Theme;

import com.mojang.blaze3d.platform.InputConstants;
import com.quickactions.api.ActionCategory;
import com.quickactions.config.QuickActionsConfig;
import com.quickactions.ui.ListProbe;
import com.quickactions.ui.ListProbe.EntryLookup;
import com.quickactions.ui.ListProbe.Span;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.UiText;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PackDragReorder {
    private static final int DRAG_THRESHOLD = 5;
    private static final int ICON_ZONE_WIDTH = 32;

    private static final int COLOR_GHOST_SHADOW = 0x55000000;
    private static final int COLOR_SOURCE_DIM = 0x66000000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private final Screen screen;

    private @Nullable TransferableSelectionList sourceList;
    private @Nullable String sourceId;
    private Component sourceTitle = Component.empty();
    private double pressX;
    private double pressY;
    private int anchorX;
    private int grabOffsetY;
    private int rowLeft;
    private int rowRight;
    private int rowHeight;
    private int stride;
    private boolean dragging;
    private int dropIndex = -1;

    private Object cachedEntry;
    private Span cachedSpan;
    private boolean hasSourceTop;
    private int sourceTop;

    private PackDragReorder(Screen screen) {
        this.screen = screen;
    }

    public static void install(Screen screen) {
        new PackDragReorder(screen).register();
    }

    private void register() {
        ScreenMouseEvents.allowMouseClick(screen).register(this::onPress);
        ScreenMouseEvents.allowMouseRelease(screen).register(this::onRelease);
        ScreenKeyboardEvents.allowKeyPress(screen).register(this::onKey);
        ScreenEvents.afterExtract(screen).register(this::onExtract);
    }

    // ---------- helpers ----------

    private static List<TransferableSelectionList.PackEntry> packEntries(TransferableSelectionList list) {
        List<TransferableSelectionList.PackEntry> result = new ArrayList<>();
        for (var child : list.children()) {
            if (child instanceof TransferableSelectionList.PackEntry entry) {
                result.add(entry);
            }
        }
        return result;
    }

    private static int indexOfId(List<TransferableSelectionList.PackEntry> entries, String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getPackId().equals(id)) return i;
        }
        return -1;
    }

    // ---------- input ----------

    private boolean onPress(Screen s, MouseButtonEvent event) {
        cancel();
        if (event.button() != MouseButtons.LEFT) return true;
        if (!QuickActionsConfig.get().packDragReorder || !ActionCategory.RESOURCE_PACK.isEnabled()) return true;

        int x = (int) event.x();
        int y = (int) event.y();

        for (TransferableSelectionList list : ResourcePackIntegration.lists(screen)) {
            Object entry = list.getEntryAtPosition(x, y);
            if (!(entry instanceof TransferableSelectionList.PackEntry packEntry)) continue;

            // drag only enabled unpinned packs
            PackSelectionModel.Entry model = ResourcePackIntegration.modelOf(packEntry);
            if (!model.isSelected() || model.isFixedPosition()) return true;

            EntryLookup lookup = list::getEntryAtPosition;
            Span horizontal = ListProbe.horizontalSpan(lookup, entry, x, y, screen.width);
            if (x - horizontal.start() < ICON_ZONE_WIDTH) return true;
            Span vertical = ListProbe.verticalSpan(lookup, entry, x, y, screen.height);

            sourceList = list;
            sourceId = packEntry.getPackId();
            sourceTitle = model.getTitle();
            pressX = event.x();
            pressY = event.y();
            anchorX = x;
            grabOffsetY = y - vertical.start();
            rowLeft = horizontal.start();
            rowRight = horizontal.end();
            rowHeight = vertical.length();
            stride = ListProbe.measureStride(lookup, entry, x, vertical);
            cachedEntry = entry;
            cachedSpan = vertical;
            sourceTop = vertical.start();
            hasSourceTop = true;
            return true;
        }
        return true;
    }

    private boolean onRelease(Screen s, MouseButtonEvent event) {
        if (event.button() != MouseButtons.LEFT) return true;

        boolean wasDragging = dragging;
        TransferableSelectionList list = sourceList;
        String id = sourceId;
        int target = dropIndex;
        cancel();

        if (!wasDragging || list == null || id == null) return true;
        if (target >= 0) {
            movePack(list, id, target);
        }
        return false; // eat the release after drag
    }

    private boolean onKey(Screen s, KeyEvent event) {
        if (dragging && event.key() == InputConstants.KEY_ESCAPE) {
            cancel();
            return false;
        }
        return true;
    }

    private void cancel() {
        sourceList = null;
        sourceId = null;
        dragging = false;
        dropIndex = -1;
        cachedEntry = null;
        cachedSpan = null;
        hasSourceTop = false;
    }

    // ---------- moving ----------

    private void movePack(TransferableSelectionList list, String id, int target) {
        List<TransferableSelectionList.PackEntry> entries = packEntries(list);
        int from = indexOfId(entries, id);
        if (from < 0 || target < 0 || target >= entries.size() || from == target) return;

        PackSelectionModel.Entry model = ResourcePackIntegration.modelOf(entries.get(from));
        int steps = Math.abs(target - from);

        if (target > from) {
            for (int i = 0; i < steps && model.canMoveDown(); i++) {
                model.moveDown();
            }
        } else {
            for (int i = 0; i < steps && model.canMoveUp(); i++) {
                model.moveUp();
            }
        }
    }

    // ---------- render ----------

    private void onExtract(Screen s, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        TransferableSelectionList list = sourceList;
        String id = sourceId;
        if (list == null || id == null) return;

        if (!dragging) {
            if (Math.hypot(mouseX - pressX, mouseY - pressY) < DRAG_THRESHOLD) return;
            dragging = true;
        }

        if (!screen.children().contains(list)) { // screen recreated
            cancel();
            return;
        }
        List<TransferableSelectionList.PackEntry> entries = packEntries(list);
        int from = indexOfId(entries, id);
        if (from < 0) {
            cancel();
            return;
        }
        EntryLookup lookup = list::getEntryAtPosition;

        dropIndex = -1;
        Span hoveredSpan = null;
        int hoveredIndex = -1;
        Object hovered = lookup.entryAt(anchorX, mouseY);
        if (hovered instanceof TransferableSelectionList.PackEntry hoveredEntry) {
            if (hovered == cachedEntry && cachedSpan != null
                    && ListProbe.isValid(lookup, hovered, anchorX, cachedSpan)) {
                hoveredSpan = cachedSpan;
            } else {
                hoveredSpan = ListProbe.verticalSpan(lookup, hovered, anchorX, mouseY, screen.height);
                cachedEntry = hovered;
                cachedSpan = hoveredSpan;
            }
            hoveredIndex = indexOfId(entries, hoveredEntry.getPackId());
            boolean fixed = ResourcePackIntegration.modelOf(hoveredEntry).isFixedPosition();
            if (hoveredIndex >= 0 && hoveredIndex != from && !fixed) {
                dropIndex = hoveredIndex;
            }
        }

        if (hoveredSpan != null && hoveredIndex >= 0) {
            sourceTop = hoveredSpan.start() + (from - hoveredIndex) * stride;
            hasSourceTop = true;
        }

        graphics.nextStratum(); // above screen widgets

        if (hasSourceTop) {
            int listTop = list.getY();
            int listBottom = listTop + list.getHeight();
            if (sourceTop >= listTop && sourceTop + stride <= listBottom) {
                int bottom = sourceTop + Math.max(1, rowHeight - 4);
                graphics.fill(rowLeft, sourceTop, rowRight, bottom, COLOR_SOURCE_DIM);
            }
        }

        if (dropIndex >= 0 && hoveredSpan != null) {
            int lineY = dropIndex < from ? hoveredSpan.start() : hoveredSpan.end();
            graphics.fill(rowLeft + 4, lineY - 2, rowRight - 4, lineY + 2, Theme.accentAlpha(0x33));
            graphics.fill(rowLeft + 4, lineY - 1, rowRight - 4, lineY + 1, Theme.accent());
            graphics.fill(rowLeft + 2, lineY - 2, rowLeft + 6, lineY + 2, Theme.accent());
            graphics.fill(rowRight - 6, lineY - 2, rowRight - 2, lineY + 2, Theme.accent());
        }

        // 3. card that follows the cursor
        drawGhost(graphics, mouseY);
    }

    private void drawGhost(GuiGraphicsExtractor graphics, int mouseY) {
        Font font = Minecraft.getInstance().font;

        int height = Math.max(20, rowHeight - 4);
        int top = Math.max(0, Math.min(mouseY - grabOffsetY, screen.height - height));
        int left = rowLeft + 2;
        int right = rowRight - 2;

        graphics.fill(left + 2, top + 2, right + 2, top + height + 2, COLOR_GHOST_SHADOW);
        graphics.fill(left - 1, top - 1, right + 1, top + height + 1, Theme.accent());
        graphics.fill(left, top, right, top + height, Theme.ghostBg());

        // 2x3 dot handle
        int gripX = left + 8;
        int gripY = top + (height - 10) / 2;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int x = gripX + col * 4;
                int y = gripY + row * 4;
                graphics.fill(x, y, x + 2, y + 2, Theme.tone(0.45f));
            }
        }

        String name = UiText.fitEnd(font, sourceTitle.getString(), right - left - 28);
        graphics.text(font, Component.literal(name), left + 22, top + (height - font.lineHeight) / 2,
                COLOR_TEXT, true);
    }
}