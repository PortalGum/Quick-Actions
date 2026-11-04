package com.quickactions.actions.screenshot.manager;

import com.quickactions.ui.Icons;
import com.quickactions.ui.UiText;
import com.quickactions.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static com.quickactions.actions.screenshot.manager.ShotPaint.WHITE;
import static com.quickactions.actions.screenshot.manager.ShotPaint.font;
import static com.quickactions.actions.screenshot.manager.ShotPaint.lerpColor;

/** top bar: camera icon, title, subtitle (counts) and the "Select" pill */
final class HeaderBar {
    private final ShotModel model;
    private final GalleryLayout layout;
    private final Component title;
    private float pillHover;

    HeaderBar(ShotModel model, GalleryLayout layout, Component title) {
        this.model = model;
        this.layout = layout;
        this.title = title;
    }

    Rect selectPillRect() {
        Component label = Component.translatable(model.selectMode
                ? "quickactions.shots.select.cancel" : "quickactions.shots.select");
        return new Rect(layout.galleryLeft, 10, 16 + font().width(label) + 8, 17);
    }

    /** pill hides when the window is so narrow it would touch the title */
    boolean pillVisible() {
        if (model.loading || model.shots.isEmpty()) return false;
        int titleLeft = (layout.width - (Icons.CAMERA_WIDTH + 7 + font().width(title))) / 2;
        Rect pill = selectPillRect();
        return pill.x() + pill.w() + 8 < titleLeft;
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int titleWidth = font().width(title);
        int total = Icons.CAMERA_WIDTH + 7 + titleWidth;
        int x = (layout.width - total) / 2;
        Icons.camera(g, x, 11, Theme.subtle(), Theme.panelOpaque(), Theme.accent());
        g.text(font(), title, x + Icons.CAMERA_WIDTH + 7, 12, WHITE, true);

        Component subtitle;
        int subtitleColor = Theme.muted();
        if (model.loading) {
            subtitle = Component.translatable("quickactions.shots.subtitle.loading");
        } else if (model.selectMode) {
            subtitle = Component.translatable("quickactions.shots.subtitle.selected",
                    model.checked.size(), model.size(), UiText.formatBytes(model.checkedBytes));
            subtitleColor = Theme.accentLight();
        } else {
            subtitle = Component.translatable("quickactions.shots.subtitle",
                    model.size(), UiText.formatBytes(model.totalBytes));
        }
        g.text(font(), subtitle, (layout.width - font().width(subtitle)) / 2,
                12 + font().lineHeight + 3, subtitleColor, true);

        if (pillVisible()) drawSelectPill(g, mouseX, mouseY);
    }

    private void drawSelectPill(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean selectMode = model.selectMode;
        Rect r = selectPillRect();
        boolean hovered = !model.viewerOpen() && r.contains(mouseX, mouseY);
        pillHover += ((hovered ? 1f : 0f) - pillHover) * 0.35f;

        int border = lerpColor(selectMode ? Theme.accentSoft() : Theme.border(), Theme.accent(), pillHover);
        int fill = selectMode
                ? lerpColor(Theme.accentMix(0.24f), Theme.accentMix(0.32f), pillHover)
                : lerpColor(Theme.card(), Theme.cardHover(), pillHover);
        ShotPaint.roundBox(g, r, border, fill);

        int cx = r.x() + 9;
        int cy = r.y() + r.h() / 2;
        if (selectMode) {
            Icons.cross(g, cx - 3, cy - 3, 6, WHITE);
        } else {
            Icons.ringCircle(g, cx, cy, 4, 1, Theme.accentLight());
        }
        Component label = Component.translatable(selectMode
                ? "quickactions.shots.select.cancel" : "quickactions.shots.select");
        g.text(font(), label, r.x() + 17, r.y() + (r.h() - font().lineHeight) / 2 + 1,
                selectMode ? WHITE : lerpColor(Theme.muted(), WHITE, pillHover), true);
    }
}
