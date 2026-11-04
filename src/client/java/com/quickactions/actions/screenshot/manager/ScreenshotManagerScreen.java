package com.quickactions.actions.screenshot.manager;

import com.mojang.blaze3d.platform.InputConstants;
import com.quickactions.actions.screenshot.ScreenshotActions;
import com.quickactions.actions.screenshot.ScreenshotInfo;
import com.quickactions.actions.screenshot.ScreenshotLibrary;
import com.quickactions.actions.screenshot.ScreenshotOperations;
import com.quickactions.actions.screenshot.ScreenshotTarget;
import com.quickactions.actions.screenshot.ScreenshotTextures;
import com.quickactions.ui.ContextMenuHost;
import com.quickactions.ui.Cursors;
import com.quickactions.ui.HelpTip;
import com.quickactions.ui.MenuItem;
import com.quickactions.ui.MouseButtons;
import com.quickactions.ui.QuickActionMenu;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.quickactions.actions.screenshot.manager.GalleryLayout.ZONE_CHECK;
import static com.quickactions.actions.screenshot.manager.GalleryLayout.ZONE_EXPAND;

/**
 * screenshot gallery screen, opened from the title screen
 *
 * this class only wires stuff together: makes the parts, passes input to them,
 * draws them in order, runs the bottom buttons and context menu actions
 * the parts:
 *  {@link ShotModel} - the list, selection, check marks
 *  {@link GalleryLayout} - where everything is, scroll position, hit tests
 *  {@link GalleryPainter}, {@link DetailPanel}, {@link HeaderBar} - drawing
 *  {@link ScrollInteraction}, {@link PointerGestures} - scrollbar, autoscroll, drag box, long press
 *  {@link ImageViewer} (+ {@link ViewerZoom}) - the fullscreen view
 */
public class ScreenshotManagerScreen extends Screen {
    private static final ScreenshotActions ACTIONS = new ScreenshotActions();

    private final Screen parent;

    private final ShotModel model = new ShotModel(this::updateButtons);
    private final GalleryLayout layout = new GalleryLayout(model);
    private final ScrollInteraction scrolling = new ScrollInteraction(model, layout);
    private final PointerGestures gestures = new PointerGestures(model, layout);
    private final PendingTooltip tooltip = new PendingTooltip();
    private final GalleryPainter painter = new GalleryPainter(model, layout, gestures, scrolling, tooltip);
    private final DetailPanel detail = new DetailPanel(model, layout, tooltip);
    private final ImageViewer imageViewer =
            new ImageViewer(model, layout, shot -> select(shot, true), this::deleteSelected);
    private final HeaderBar headerBar;

    private boolean started;
    private long lastNanos;

    // bottom button bar
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
        this.headerBar = new HeaderBar(model, layout, this.title);
    }

    // ---------- init ----------

    @Override
    protected void init() {
        layout.configure(this.width, this.height);

        barWidth = Math.min(this.width - 24, 500);
        barLeft = this.width / 2 - barWidth / 2;
        barRow = this.height - 28;
        folderButton = addButton("quickactions.shots.button.show_in_folder",
                () -> { if (model.selectMode) model.toggleSelectAll(); else showInFolderSelected(); });
        copyButton = addButton("quickactions.shots.button.copy_path", this::copyPathSelected);
        deleteButton = addButton("quickactions.shots.button.delete",
                () -> { if (model.selectMode) deleteChecked(); else deleteSelected(); });
        doneButton = addButton("quickactions.shots.button.done",
                () -> { if (model.selectMode) model.exitSelectMode(); else onClose(); });
        gestures.reset();
        scrolling.reset();

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
        model.beginLoading();
        CompletableFuture.supplyAsync(() -> {
            try {
                return ScreenshotLibrary.scan();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((list, error) -> Minecraft.getInstance().execute(() -> {
            model.applyLoaded(list, error);
            rebuildLayout();
            updateButtons();
        }));
    }

    private void rebuildLayout() {
        layout.rebuild(this.font.lineHeight);
        painter.sync(model.size());
    }

    // ---------- select and buttons ----------

    private void select(int shot, boolean scrollTo) {
        model.selected = shot;
        if (scrollTo) layout.ensureVisible(shot);
        updateButtons();
    }

    private void updateButtons() {
        if (folderButton == null) return;
        boolean has = model.selectedInfo() != null;

        if (model.selectMode) {
            int third = (barWidth - 8) / 3;
            place(folderButton, 0, third);
            place(deleteButton, 1, third);
            place(doneButton, 2, third);
            copyButton.visible = false;
            copyButton.active = false;

            boolean all = !model.shots.isEmpty() && model.checked.size() == model.size();
            folderButton.setMessage(Component.translatable(all
                    ? "quickactions.shots.button.deselect_all" : "quickactions.shots.button.select_all"));
            folderButton.active = !model.shots.isEmpty();
            deleteButton.setMessage(model.checked.isEmpty()
                    ? Component.translatable("quickactions.shots.button.delete")
                    : Component.translatable("quickactions.shots.button.delete_count", model.checked.size()));
            deleteButton.active = !model.checked.isEmpty();
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

    // ---------- actions ----------

    private ScreenshotTarget targetFor(ScreenshotInfo info) {
        return new ScreenshotTarget(info, this, () -> removeShots(List.of(info)), newFile -> replaceShot(info, newFile));
    }

    private void showInFolderSelected() {
        ScreenshotInfo info = model.selectedInfo();
        if (info != null) ScreenshotOperations.showInFolder(targetFor(info));
    }

    private void copyPathSelected() {
        ScreenshotInfo info = model.selectedInfo();
        if (info != null) ScreenshotOperations.copyPath(targetFor(info));
    }

    /** the shot open in the viewer, or the selected one */
    private void deleteSelected() {
        int viewer = model.viewer;
        ScreenshotInfo info = viewer >= 0 && viewer < model.size() ? model.shots.get(viewer) : model.selectedInfo();
        if (info != null) ScreenshotOperations.delete(targetFor(info));
    }

    private void deleteChecked() {
        List<ScreenshotInfo> list = model.checkedInfos();
        if (list.isEmpty()) return;
        if (list.size() == 1) {
            ScreenshotOperations.delete(targetFor(list.get(0)));
        } else {
            ScreenshotOperations.deleteMany(this, list, this::removeShots);
        }
    }

    /** called after the files are gone */
    private void removeShots(Collection<ScreenshotInfo> gone) {
        model.remove(gone);
        rebuildLayout();
        layout.ensureVisible(model.selected);
        updateButtons();
    }

    /** called after a rename */
    private void replaceShot(ScreenshotInfo old, Path newFile) {
        if (!model.replace(old, newFile)) {
            startLoading();
            return;
        }
        rebuildLayout();
        layout.ensureVisible(model.selected);
        updateButtons();
    }

    // ---------- context menu ----------

    private MenuItem bulkMenu() {
        return MenuItem.submenu(Component.translatable("quickactions.menu.title"), List.of(
                MenuItem.action(Component.translatable("quickactions.shots.bulk.delete", model.checked.size()), true,
                        this::deleteChecked),
                MenuItem.action(Component.translatable("quickactions.shots.bulk.deselect"), true,
                        model::exitSelectMode)));
    }

    private @Nullable List<MenuItem> menuAt(double x, double y) {
        scrolling.stopAutoScroll();
        if (!ACTIONS.category().isEnabled()) return null;

        int shot = -1;
        if (model.viewerOpen()) {
            shot = model.viewer;
        } else if (layout.inGallery(x, y)) {
            shot = layout.shotAt(x, y);
            if (shot >= 0) {
                boolean inSelection = model.selectMode && model.checked.size() > 1
                        && model.checked.contains(model.shots.get(shot).file());
                select(shot, false);
                if (inSelection) return List.of(bulkMenu());
            }
        } else if (layout.showDetail && layout.previewRect().contains(x, y)) {
            shot = model.selected;
        }
        if (shot < 0 || shot >= model.size()) return null;
        return List.of(QuickActionMenu.build(ACTIONS, targetFor(model.shots.get(shot))));
    }

    // ---------- mouse ----------

    /** @return false to eat the click */
    private boolean onClick(Screen screen, MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        boolean ctrl = (event.modifiers() & PointerGestures.MOD_CONTROL) != 0;
        boolean shift = (event.modifiers() & PointerGestures.MOD_SHIFT) != 0;

        if (scrolling.isAutoScrolling()) {
            scrolling.stopAutoScroll();
            return false;
        }

        if (imageViewer.isOpen()) {
            imageViewer.onClick(mx, my, button);
            return false;
        }

        if (button == MouseButtons.MIDDLE) {
            if (layout.inGallery(mx, my) && scrolling.geometry() != null) {
                scrolling.startAutoScroll(mx, my);
                gestures.cancelPress();
                return false;
            }
            return true;
        }

        if (button != MouseButtons.LEFT) return true; // rmb already handled by ContextMenuHost

        if (scrolling.overScrollbar(mx, my)) {
            scrolling.beginBarDrag(my);
            gestures.resetClick();
            return false;
        }

        if (headerBar.pillVisible() && headerBar.selectPillRect().contains(mx, my)) {
            model.toggleSelectMode();
            return false;
        }

        int headerCell = layout.headerAt(mx, my);
        if (headerCell >= 0) {
            Cell dayHeader = layout.cells.get(headerCell);
            if (model.selectMode || layout.headerCheckRect(dayHeader).contains(mx, my)) {
                model.toggleDay(dayHeader, layout.firstShotOf(headerCell));
                gestures.resetClick();
                return false;
            }
            return true;
        }

        int shot = layout.shotAt(mx, my);
        if (shot >= 0) {
            clickCard(shot, mx, my, ctrl, shift);
            return false;
        }
        if (layout.showDetail && model.selectedInfo() != null && layout.previewRect().contains(mx, my)) {
            imageViewer.open(model.selected);
            return false;
        }
        return true;
    }

    /** left click on a card: check circle, expand button, ctrl/shift pick, or plain select / double click */
    private void clickCard(int shot, double mx, double my, boolean ctrl, boolean shift) {
        int zone = layout.zoneAt(shot, mx, my);
        if (zone == ZONE_CHECK && !model.selectMode) {
            model.toggleCheck(shot);
            select(shot, false);
            gestures.resetClick();
        } else if (zone == ZONE_EXPAND) {
            select(shot, false);
            imageViewer.open(shot);
        } else if (ctrl) {
            model.toggleCheck(shot);
            select(shot, false);
            gestures.resetClick();
        } else if (shift && (model.selectMode || model.selected >= 0)) {
            if (!model.selectMode && model.anchor < 0) model.anchor = model.selected;
            model.checkRange(shot);
            select(shot, false);
            gestures.resetClick();
        } else if (model.selectMode) {
            select(shot, false);
            gestures.beginGesture(shot, mx, my, true);
            gestures.resetClick();
        } else {
            select(shot, false);
            if (gestures.press(shot, mx, my)) {
                imageViewer.open(shot);
            }
        }
    }

    private boolean onRelease(Screen screen, MouseButtonEvent event) {
        if (event.button() == MouseButtons.LEFT) imageViewer.onLeftRelease();
        if (event.button() == MouseButtons.MIDDLE) {
            scrolling.onMiddleRelease();
            return true;
        }
        if (event.button() != MouseButtons.LEFT) return true;
        scrolling.endBarDrag();
        gestures.onLeftRelease();
        return true;
    }

    private boolean onScroll(Screen screen, double mouseX, double mouseY, double horizontal, double vertical) {
        if (scrolling.isAutoScrolling()) scrolling.stopAutoScroll();
        if (imageViewer.isOpen()) {
            imageViewer.onScroll(mouseX, mouseY, vertical);
            return false;
        }
        if (layout.inGallery(mouseX, mouseY)) {
            layout.scrollTarget -= (float) Math.signum(vertical) * GalleryLayout.WHEEL_STEP;
            layout.clampScroll();
            return false;
        }
        return true;
    }

    // ---------- keyboard ----------

    private boolean onKeyRelease(Screen screen, KeyEvent event) {
        imageViewer.onKeyRelease(event.key(), event.modifiers());
        return true;
    }

    /** @return false to eat the key */
    private boolean onKey(Screen screen, KeyEvent event) {
        int key = event.key();
        imageViewer.onKeyPress(key, event.modifiers());

        if (scrolling.isAutoScrolling() && key == InputConstants.KEY_ESCAPE) {
            scrolling.stopAutoScroll();
            return false;
        }

        if (imageViewer.isOpen()) {
            return !imageViewer.onKey(key);
        }

        if (model.shots.isEmpty()) return true;

        if (key == InputConstants.KEY_ESCAPE && model.selectMode) {
            model.exitSelectMode();
            return false;
        }
        if (key == InputConstants.KEY_SPACE && model.selected >= 0) {
            model.toggleCheck(model.selected);
            return false;
        }

        int selected = model.selected;
        int from = Math.max(selected, 0);
        int target = -1;
        if (key == InputConstants.KEY_LEFT) target = selected < 0 ? 0 : from - 1;
        else if (key == InputConstants.KEY_RIGHT) target = selected < 0 ? 0 : from + 1;
        else if (key == InputConstants.KEY_UP) target = selected < 0 ? 0 : from - layout.columns;
        else if (key == InputConstants.KEY_DOWN) target = selected < 0 ? 0 : from + layout.columns;
        else if (key == InputConstants.KEY_HOME) target = 0;
        else if (key == InputConstants.KEY_END) target = model.size() - 1;
        boolean isNavigation = key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT
                || key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN
                || key == InputConstants.KEY_HOME || key == InputConstants.KEY_END;
        if (isNavigation) {
            select(Math.max(0, Math.min(target, model.size() - 1)), true);
            return false;
        }
        if (key == InputConstants.KEY_RETURN && selected >= 0) {
            imageViewer.open(selected);
            return false;
        }
        if (key == InputConstants.KEY_DELETE) {
            if (model.selectMode && !model.checked.isEmpty()) {
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
        tooltip.text = null;

        gestures.updateLongPress(mouseX, mouseY);
        gestures.updateGesture(mouseX, mouseY, dt);
        if (model.viewerOpen()) {
            scrolling.cancelWhileViewerOpen();
        }
        scrolling.updateBarDrag(mouseY);
        scrolling.updateAutoScroll(mouseY, dt);
        painter.tick(dt);
        layout.tickScroll(dt);

        headerBar.draw(g, mouseX, mouseY);
        painter.draw(g, mouseX, mouseY, dt);
        if (layout.showDetail) detail.draw(g, mouseX, mouseY, dt);
        if (scrolling.isAutoScrolling()) {
            scrolling.drawAutoScrollAnchor(g, mouseY);
            Cursors.resizeVertical(g);
            tooltip.text = null;
        }
        if (model.viewerOpen()) imageViewer.draw(g, mouseX, mouseY, dt);

        if (tooltip.text != null) {
            HelpTip.renderTooltip(g, this.font, Component.literal(tooltip.text), mouseX, mouseY,
                    this.width, this.height);
        }
    }
}
