package com.quickactions.ui;

import com.quickactions.ui.theme.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ContextMenu {
    private static final int ITEM_HEIGHT = 20;
    private static final int PADDING = 4;
    private static final int TEXT_PADDING = 8;
    private static final int ARROW_WIDTH = 12;
    private static final int CHECKBOX_SIZE = 9;
    private static final int CHECK_COLUMN = CHECKBOX_SIZE + 6;
    private static final int HINT_COLUMN = HelpTip.SIZE + 8;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DISABLED = 0xFF777777;

    private static final int COLOR_TICK = 0xFFFFFFFF;

    /** check mark pixels inside 9x9 checkbox */
    private static final int[][] TICK = {
            {2, 4}, {3, 5}, {4, 4}, {5, 3}, {6, 2},
            {2, 5}, {3, 6}, {4, 5}, {5, 4}, {6, 3}
    };

    private final Screen screen;
    private final Font font = Minecraft.getInstance().font;
    private final List<Panel> panels = new ArrayList<>();

    public ContextMenu(Screen screen, List<MenuItem> rootItems, int x, int y) {
        this.screen = screen;
        panels.add(createPanel(rootItems, x, y, null));
    }

    // ---------- state ----------

    private static final class Panel {
        final List<MenuItem> items;
        final int x, y, width, height;
        final int checkColumn;
        final int arrowColumn;
        final boolean hasHints;
        int hovered = -1;
        int openedIndex = -1; // item whose submenu is open

        Panel(List<MenuItem> items, int x, int y, int width, int height,
              int checkColumn, int arrowColumn, boolean hasHints) {
            this.items = items;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.checkColumn = checkColumn;
            this.arrowColumn = arrowColumn;
            this.hasHints = hasHints;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }

        int itemAt(double mx, double my) {
            if (!contains(mx, my)) return -1;
            int index = (int) Math.floor((my - y - PADDING) / ITEM_HEIGHT);
            return index >= 0 && index < items.size() ? index : -1;
        }

        int markerX() {
            return x + width - TEXT_PADDING - arrowColumn - HelpTip.SIZE;
        }

        int markerY(int index) {
            return y + PADDING + index * ITEM_HEIGHT + (ITEM_HEIGHT - HelpTip.SIZE) / 2;
        }
    }

    private Panel createPanel(List<MenuItem> items, int anchorX, int anchorY, @Nullable Panel parent) {
        int maxText = 0;
        boolean hasChildren = false;
        boolean hasCheckable = false;
        boolean hasHints = false;
        for (MenuItem item : items) {
            maxText = Math.max(maxText, font.width(item.label()));
            hasChildren |= item.hasChildren();
            hasCheckable |= item.checked() != null;
            hasHints |= item.hint() != null;
        }
        int checkColumn = hasCheckable ? CHECK_COLUMN : 0;
        int arrowColumn = hasChildren ? ARROW_WIDTH : 0;
        int hintColumn = hasHints ? HINT_COLUMN : 0;

        int width = maxText + TEXT_PADDING * 2 + checkColumn + hintColumn + arrowColumn;
        int height = items.size() * ITEM_HEIGHT + PADDING * 2;

        int x = anchorX;
        int y = anchorY;
        if (x + width > screen.width) {
            x = parent != null ? parent.x - width + 2 : screen.width - width;
        }
        if (y + height > screen.height) {
            y = screen.height - height;
        }
        return new Panel(items, Math.max(0, x), Math.max(0, y), width, height, checkColumn, arrowColumn, hasHints);
    }

    // ---------- input ----------

    public boolean isInside(double mx, double my) {
        for (Panel panel : panels) {
            if (panel.contains(mx, my)) return true;
        }
        return false;
    }

    public @Nullable MenuItem itemAt(double mx, double my) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (panel.contains(mx, my)) {
                int index = panel.itemAt(mx, my);
                return index >= 0 ? panel.items.get(index) : null;
            }
        }
        return null;
    }

    private @Nullable Component hintAt(double mx, double my) {
        for (Panel panel : panels) {
            if (!panel.hasHints) continue;
            for (int i = 0; i < panel.items.size(); i++) {
                Component hint = panel.items.get(i).hint();
                if (hint != null && HelpTip.isOver(mx, my, panel.markerX(), panel.markerY(i))) {
                    return hint;
                }
            }
        }
        return null;
    }

    /** click on help icon runs nothing */
    public boolean isOverHint(double mx, double my) {
        return hintAt(mx, my) != null;
    }

    private void updateHover(double mx, double my) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (!panel.contains(mx, my)) continue;

            int index = panel.itemAt(mx, my);
            panel.hovered = index;

            if (index >= 0 && index == panel.openedIndex && panels.size() > i + 1) {
                return; // submenu of this item is already open
            }

            while (panels.size() > i + 1) {
                panels.remove(panels.size() - 1);
            }
            panel.openedIndex = -1;

            if (index >= 0) {
                MenuItem item = panel.items.get(index);
                if (item.enabled() && item.hasChildren()) {
                    int rowTop = panel.y + PADDING + index * ITEM_HEIGHT;
                    panels.add(createPanel(item.children(), panel.x + panel.width - 2, rowTop - PADDING, panel));
                    panel.openedIndex = index;
                }
            }
            return;
        }
        // when moving diagonally
    }

    // ---------- render ----------

    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        updateHover(mouseX, mouseY);
        graphics.nextStratum(); // draw above other screen elements

        for (Panel panel : panels) {
            graphics.fill(panel.x - 1, panel.y - 1, panel.x + panel.width + 1, panel.y + panel.height + 1, Theme.border());
            graphics.fill(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, Theme.panelBg());

            for (int i = 0; i < panel.items.size(); i++) {
                MenuItem item = panel.items.get(i);
                int rowTop = panel.y + PADDING + i * ITEM_HEIGHT;

                if (i == panel.hovered && item.enabled()) {
                    graphics.fill(panel.x + 2, rowTop, panel.x + panel.width - 2, rowTop + ITEM_HEIGHT, Theme.accentDeep());
                }

                int color = item.enabled() ? COLOR_TEXT : COLOR_TEXT_DISABLED;
                int textY = rowTop + (ITEM_HEIGHT - font.lineHeight) / 2;

                if (item.checked() != null) {
                    int boxY = rowTop + (ITEM_HEIGHT - CHECKBOX_SIZE) / 2;
                    drawCheckbox(graphics, panel.x + TEXT_PADDING, boxY, item.checked().getAsBoolean(), item.enabled());
                }

                graphics.text(font, item.label(), panel.x + TEXT_PADDING + panel.checkColumn, textY, color, true);

                if (item.hint() != null) {
                    int markerX = panel.markerX();
                    int markerY = panel.markerY(i);
                    HelpTip.drawMarker(graphics, font, markerX, markerY,
                            HelpTip.isOver(mouseX, mouseY, markerX, markerY));
                }

                if (item.hasChildren()) {
                    graphics.text(font, ">", panel.x + panel.width - ARROW_WIDTH, textY, color, true);
                }
            }
        }

        // tooltip drawn last, over all panels
        Component tip = hintAt(mouseX, mouseY);
        if (tip != null) {
            HelpTip.renderTooltip(graphics, font, tip, mouseX, mouseY, screen.width, screen.height);
        }
    }

    private void drawCheckbox(GuiGraphicsExtractor graphics, int x, int y, boolean checked, boolean enabled) {
        int border = enabled ? Theme.subtle() : Theme.scroll();
        int fill = !checked ? Theme.deep() : (enabled ? Theme.accent() : Theme.scroll());

        graphics.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, border);
        graphics.fill(x + 1, y + 1, x + CHECKBOX_SIZE - 1, y + CHECKBOX_SIZE - 1, fill);

        if (checked) {
            for (int[] p : TICK) {
                graphics.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, COLOR_TICK);
            }
        }
    }
}