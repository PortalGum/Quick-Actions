package com.quickactions.ui;

import com.quickactions.ui.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PickerList<T> {

    public record Row<T>(
            @Nullable T item,
            Component title,
            Component subtitle,
            @Nullable Component badge,
            @Nullable Component actionTooltip,
            @Nullable Runnable action,
            @Nullable Supplier<@Nullable Identifier> icon) {

        public static <V> Row<V> header(Component title) {
            return new Row<>(null, title, Component.empty(), null, null, null, null);
        }

        public static <V> Row<V> warningHeader(Component title, Component badge, Component actionTooltip,
                                               Runnable action) {
            return new Row<>(null, title, Component.empty(), badge, actionTooltip, action, null);
        }

        public static <V> Row<V> iconHeader(Component title, Component subtitle,
                                            Supplier<@Nullable Identifier> icon,
                                            @Nullable Component badge, @Nullable Component actionTooltip,
                                            @Nullable Runnable action) {
            return new Row<>(null, title, subtitle, badge, actionTooltip, action, icon);
        }

        public static <V> Row<V> item(V item, Component title, Component subtitle) {
            return new Row<>(item, title, subtitle, null, null, null, null);
        }

        public boolean isHeader() {
            return item == null;
        }
    }

    private static final int ITEM_HEIGHT = 30;
    private static final int HEADER_HEIGHT = 20;
    private static final int ICON_HEADER_HEIGHT = 42;
    private static final int WORLD_ICON = 32;
    private static final int WORLD_ICON_PAD = 5;
    private static final int ICON_WIDTH = 9;
    private static final int ICON_HEIGHT = 10;
    private static final int ICON_MARGIN_RIGHT = 14;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_WARNING = 0xFFFF7B7B;
    private static final int COLOR_WARNING_BG = 0x44FF5555;
    private static final int COLOR_TRASH = 0xFFB86A6A;
    private static final int COLOR_TRASH_HOVER = 0xFFFF6B6B;
    private static final int COLOR_TRASH_GLOW = 0x22FF6B6B;

    private final Consumer<T> onSelect;
    private List<Row<T>> rows = List.of();
    private @Nullable T selected;
    private int firstRow;
    private int left;
    private int top;
    private int width;
    private int height;
    private Component emptyTitle = Component.empty();
    private Component emptyHint = Component.empty();

    public PickerList(Consumer<T> onSelect) {
        this.onSelect = onSelect;
    }

    // ---------- config ----------

    public void layout(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    public void setEmptyText(Component title, Component hint) {
        this.emptyTitle = title;
        this.emptyHint = hint;
    }

    public void setRows(List<Row<T>> newRows) {
        this.rows = newRows;
        if (selected != null) {
            boolean found = false;
            for (Row<T> row : rows) {
                if (Objects.equals(row.item(), selected)) {
                    found = true;
                    break;
                }
            }
            if (!found) selected = null;
        }
        clampScroll();
    }

    public @Nullable T selected() {
        return selected;
    }

    /** select item from code and scroll to it */
    public void select(T item) {
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i).item(), item)) {
                selected = item;
                firstRow = Math.min(Math.max(0, i - 1), maxFirstRow());
                return;
            }
        }
    }

    // ---------- geometry ----------

    private int rowHeight(Row<T> row) {
        if (!row.isHeader()) return ITEM_HEIGHT;
        return row.icon() != null ? ICON_HEADER_HEIGHT : HEADER_HEIGHT;
    }

    private int maxFirstRow() {
        int sum = 0;
        int index = rows.size();
        while (index > 0 && sum + rowHeight(rows.get(index - 1)) <= height) {
            sum += rowHeight(rows.get(index - 1));
            index--;
        }
        return index;
    }

    private void clampScroll() {
        firstRow = Math.max(0, Math.min(firstRow, maxFirstRow()));
    }

    private boolean contains(double mx, double my) {
        return mx >= left && mx < left + width && my >= top && my < top + height;
    }

    private int rowIndexAt(double mx, double my) {
        if (!contains(mx, my)) return -1;
        int y = top;
        for (int i = firstRow; i < rows.size(); i++) {
            int h = rowHeight(rows.get(i));
            if (y + h > top + height) break;
            if (my < y + h) return i;
            y += h;
        }
        return -1;
    }

    private int rowTopOf(int index) {
        if (index < firstRow) return -1;
        int y = top;
        for (int i = firstRow; i < rows.size(); i++) {
            int h = rowHeight(rows.get(i));
            if (y + h > top + height) return -1;
            if (i == index) return y;
            y += h;
        }
        return -1;
    }

    private int iconX() {
        return left + width - ICON_MARGIN_RIGHT - ICON_WIDTH;
    }

    private boolean overIcon(int index, double mx, double my) {
        Row<T> row = rows.get(index);
        if (row.action() == null) return false;
        int rowTop = rowTopOf(index);
        if (rowTop < 0) return false;
        int x = iconX();
        int y = rowTop + (rowHeight(row) - ICON_HEIGHT) / 2;
        return mx >= x - 3 && mx < x + ICON_WIDTH + 3 && my >= y - 3 && my < y + ICON_HEIGHT + 3;
    }

    // ---------- input ----------

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        if (button != MouseButtons.LEFT) return true;

        int index = rowIndexAt(mx, my);
        if (index >= 0) {
            Row<T> row = rows.get(index);

            Runnable action = row.action();
            if (action != null && overIcon(index, mx, my)) {
                action.run();
                return true;
            }

            T item = row.item();
            if (item != null) {
                selected = item;
                onSelect.accept(item);
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double vertical) {
        if (!contains(mx, my)) return false;
        firstRow -= (int) Math.signum(vertical);
        clampScroll();
        return true;
    }

    // ---------- render ----------

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        int right = left + width;
        g.fill(left - 1, top - 1, right + 1, top + height + 1, Theme.border());
        g.fill(left, top, right, top + height, Theme.panelBg());

        if (rows.isEmpty()) {
            int centerY = top + height / 2;
            g.text(font, emptyTitle, left + (width - font.width(emptyTitle)) / 2,
                    centerY - font.lineHeight - 2, COLOR_TEXT, true);
            g.text(font, emptyHint, left + (width - font.width(emptyHint)) / 2,
                    centerY + 4, Theme.muted(), true);
            return;
        }

        int hovered = rowIndexAt(mouseX, mouseY);
        int textWidth = width - 24;
        int lh = font.lineHeight;
        int y = top;
        int visible = 0;

        for (int i = firstRow; i < rows.size(); i++) {
            Row<T> row = rows.get(i);
            int h = rowHeight(row);
            if (y + h > top + height) break;

            T item = row.item();
            if (item == null) {
                drawHeader(g, font, row, i, y, h, textWidth, right, mouseX, mouseY);
            } else {
                if (Objects.equals(item, selected)) {
                    g.fill(left, y, right, y + h, Theme.accentSelected());
                    g.fill(left, y, left + 2, y + h, Theme.accent());
                } else if (i == hovered) {
                    g.fill(left, y, right, y + h, Theme.cardHover());
                }
                g.fill(left + 6, y + h - 1, right - 6, y + h, Theme.borderSoft());

                String title = UiText.fitEnd(font, row.title().getString(), textWidth);
                g.text(font, Component.literal(title), left + 10, y + 6, COLOR_TEXT, true);
                String subtitle = UiText.fitEnd(font, row.subtitle().getString(), textWidth);
                g.text(font, Component.literal(subtitle), left + 10, y + 6 + lh + 3, Theme.muted(), true);
            }

            y += h;
            visible++;
        }

        drawScrollbar(g, visible);
    }

    private void drawHeader(GuiGraphicsExtractor g, Font font, Row<T> row, int index, int y, int h,
                            int textWidth, int right, int mouseX, int mouseY) {
        Component badge = row.badge();
        boolean warning = badge != null;
        int lh = font.lineHeight;
        Supplier<Identifier> iconSupplier = row.icon();

        g.fill(left, y, right, y + h, Theme.card());
        g.fill(left, y, left + 2, y + h, warning ? COLOR_WARNING : Theme.accent());

        int textLeft = left + 10;
        if (iconSupplier != null) {
            int ix = left + 2 + WORLD_ICON_PAD + 1;
            int iy = y + (h - WORLD_ICON) / 2;
            drawWorldIcon(g, iconSupplier.get(), ix, iy, warning);
            textLeft = ix + WORLD_ICON + 10;
            textWidth -= textLeft - (left + 10);
        }

        int badgeWidth = badge != null ? font.width(badge) + 12 : 0;
        int iconArea = row.action() != null ? ICON_WIDTH + ICON_MARGIN_RIGHT + 6 : 0;
        int titleMax = Math.max(40, textWidth - badgeWidth - iconArea);

        boolean twoLines = iconSupplier != null;
        int block = twoLines ? lh * 2 + 3 : lh;
        int titleY = y + (h - block) / 2;

        String title = UiText.fitEnd(font, row.title().getString(), titleMax);
        g.text(font, Component.literal(title), textLeft, titleY, twoLines ? COLOR_TEXT : Theme.accentLight(), true);

        if (twoLines) {
            String subtitle = UiText.fitEnd(font, row.subtitle().getString(), Math.max(40, textWidth - iconArea));
            g.text(font, Component.literal(subtitle), textLeft, titleY + lh + 3, Theme.muted(), true);
        }

        if (badge != null) {
            int badgeX = textLeft + font.width(title) + 10;
            g.fill(badgeX - 4, titleY - 2, badgeX + font.width(badge) + 4, titleY + lh + 1, COLOR_WARNING_BG);
            g.text(font, badge, badgeX, titleY, COLOR_WARNING, true);
        }

        if (row.action() != null) {
            drawTrash(g, iconX(), y + (h - ICON_HEIGHT) / 2, overIcon(index, mouseX, mouseY));
        }
    }

    private void drawWorldIcon(GuiGraphicsExtractor g, @Nullable Identifier texture, int x, int y, boolean dimmed) {
        g.fill(x - 1, y - 1, x + WORLD_ICON + 1, y + WORLD_ICON + 1, dimmed ? Theme.borderSoft() : Theme.accentSoft());
        if (texture != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, WORLD_ICON, WORLD_ICON, WORLD_ICON, WORLD_ICON);
            if (dimmed) g.fill(x, y, x + WORLD_ICON, y + WORLD_ICON, 0x66000000); // world is gone: icon is dimmed
        } else {
            g.fill(x, y, x + WORLD_ICON, y + WORLD_ICON, Theme.deep());
            int gw = Icons.bitmapWidth(Icons.GLOBE);
            Icons.bitmap(g, x + (WORLD_ICON - gw) / 2, y + (WORLD_ICON - Icons.GLOBE.length) / 2, Icons.GLOBE,
                    Theme.scroll());
        }
    }

    /** trash icon 9x10 drawn with rects */
    private void drawTrash(GuiGraphicsExtractor g, int x, int y, boolean hovered) {
        int color = hovered ? COLOR_TRASH_HOVER : COLOR_TRASH;
        if (hovered) {
            g.fill(x - 3, y - 3, x + ICON_WIDTH + 3, y + ICON_HEIGHT + 3, COLOR_TRASH_GLOW);
        }
        g.fill(x + 3, y, x + 6, y + 1, color);               // handle
        g.fill(x, y + 1, x + 9, y + 3, color);               // lid
        g.fill(x + 1, y + 3, x + 8, y + 10, color);          // body
        g.fill(x + 3, y + 4, x + 4, y + 9, Theme.card()); // slots
        g.fill(x + 5, y + 4, x + 6, y + 9, Theme.card());
    }

    public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY,
                              int screenWidth, int screenHeight) {
        int index = rowIndexAt(mouseX, mouseY);
        if (index < 0 || !overIcon(index, mouseX, mouseY)) return;

        Component text = rows.get(index).actionTooltip();
        if (text == null) return;

        int boxWidth = font.width(text) + 12;
        int boxHeight = font.lineHeight + 8;
        int x = Math.max(4, Math.min(mouseX + 10, screenWidth - boxWidth - 4));
        int y = mouseY - boxHeight - 6;
        if (y < 4) y = mouseY + 16;

        g.nextStratum();
        g.fill(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, Theme.border());
        g.fill(x, y, x + boxWidth, y + boxHeight, Theme.panelBg());
        g.text(font, text, x + 6, y + 4, COLOR_TEXT, true);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int visible) {
        if (firstRow == 0 && visible >= rows.size()) return;
        int thumbHeight = Math.max(12, height * visible / Math.max(1, rows.size()));
        int thumbTop = top + (height - thumbHeight) * firstRow / Math.max(1, maxFirstRow());
        g.fill(left + width - 5, thumbTop, left + width - 2, thumbTop + thumbHeight, Theme.scroll());
    }
}