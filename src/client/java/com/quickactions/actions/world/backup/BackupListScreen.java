package com.quickactions.actions.world.backup;

import com.quickactions.ui.theme.Theme;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.world.WorldIcons;
import com.quickactions.ui.ChoiceScreen;
import com.quickactions.ui.PickerList;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.ui.UiText;
import com.quickactions.ui.notify.Notification;
import com.quickactions.ui.notify.Notifications;
import com.quickactions.util.ClientUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class BackupListScreen extends Screen {
    private static final int LIST_TOP = 52;
    private static final int LIST_WIDTH = 320;
    private static final int FOOTER = 84;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private final Screen parent;
    private final Runnable refresh;
    private final @Nullable String filterLevelId;
    private final PickerList<BackupInfo> list = new PickerList<>(backup -> updateButtons());

    private Button rollBackButton;
    private Button restoreNewButton;
    private Button renameButton;
    private Button deleteButton;

    public BackupListScreen(Screen parent, Runnable refresh, @Nullable String levelId, @Nullable String worldName) {
        super(levelId == null
                ? Component.translatable("quickactions.backup.list.title")
                : Component.translatable("quickactions.backup.list.title.world", worldName));
        this.parent = parent;
        this.refresh = refresh;
        this.filterLevelId = levelId;
        WorldIcons.clear(); // world icons are reread on every open
    }

    @Override
    protected void init() {
        list.layout(this.width / 2 - LIST_WIDTH / 2, LIST_TOP, LIST_WIDTH,
                Math.max(60, this.height - FOOTER - LIST_TOP));
        list.setEmptyText(Component.translatable("quickactions.backup.empty"),
                Component.translatable("quickactions.backup.empty.hint"));

        int buttonWidth = 156;
        int left = this.width / 2 - buttonWidth - 2;
        int right = this.width / 2 + 2;
        int row1 = this.height - 76;
        int row2 = this.height - 52;
        int row3 = this.height - 28;

        rollBackButton = Button.builder(Component.translatable("quickactions.backup.rollback"),
                        button -> rollBackSelected())
                .bounds(left, row1, buttonWidth, 20).build();
        restoreNewButton = Button.builder(Component.translatable("quickactions.backup.restore_new"),
                        button -> restoreSelectedAsNew())
                .bounds(right, row1, buttonWidth, 20).build();
        renameButton = Button.builder(Component.translatable("quickactions.backup.rename"),
                        button -> renameSelected())
                .bounds(left, row2, buttonWidth, 20).build();
        deleteButton = Button.builder(Component.translatable("quickactions.backup.delete"),
                        button -> deleteSelected())
                .bounds(right, row2, buttonWidth, 20).build();
        addRenderableWidget(rollBackButton);
        addRenderableWidget(restoreNewButton);
        addRenderableWidget(renameButton);
        addRenderableWidget(deleteButton);

        addRenderableWidget(Button.builder(Component.translatable("quickactions.backup.open_folder"),
                        button -> openFolder())
                .bounds(left, row3, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("quickactions.backup.done"),
                        button -> onClose())
                .bounds(right, row3, buttonWidth, 20).build());

        reload(null);

        ScreenMouseEvents.allowMouseClick(this).register(
                (s, event) -> !list.mouseClicked(event.x(), event.y(), event.button()));
        ScreenMouseEvents.allowMouseScroll(this).register(
                (s, mouseX, mouseY, horizontal, vertical) -> !list.mouseScrolled(mouseX, mouseY, vertical));
    }

    // ---------- state ----------

    private static boolean worldExists(BackupInfo backup) {
        return Files.isDirectory(BackupManager.savesDir().resolve(backup.levelId()));
    }

    private void reload(@Nullable Path select) {
        List<BackupInfo> backups = filterLevelId == null
                ? BackupManager.listAll()
                : BackupManager.listFor(filterLevelId);

        Map<String, List<BackupInfo>> byWorld = new HashMap<>();
        for (BackupInfo backup : backups) {
            byWorld.computeIfAbsent(backup.levelId(), key -> new ArrayList<>()).add(backup);
        }
        List<List<BackupInfo>> groups = new ArrayList<>(byWorld.values());
        for (List<BackupInfo> group : groups) {
            group.sort(Comparator.comparingLong(BackupInfo::createdMillis).reversed());
        }
        groups.sort((a, b) -> a.get(0).worldName().compareToIgnoreCase(b.get(0).worldName()));

        List<PickerList.Row<BackupInfo>> rows = new ArrayList<>();
        for (List<BackupInfo> group : groups) {
            BackupInfo newest = group.get(0);
            if (filterLevelId == null) {
                String levelId = newest.levelId();
                List<Path> files = new ArrayList<>();
                long totalBytes = 0;
                for (BackupInfo backup : group) {
                    files.add(backup.file());
                    totalBytes += backup.sizeBytes();
                }
                Supplier<Identifier> icon = () -> WorldIcons.get(levelId, files);
                Component title = Component.literal(newest.worldName());
                Component info = Component.translatable("quickactions.backup.header.info",
                        group.size(), UiText.formatBytes(totalBytes));
                if (worldExists(newest)) {
                    rows.add(PickerList.Row.iconHeader(title, info, icon, null, null, null));
                } else {
                    rows.add(PickerList.Row.iconHeader(
                            title,
                            info,
                            icon,
                            Component.translatable("quickactions.backup.world_missing"),
                            Component.translatable("quickactions.backup.delete_all.tooltip"),
                            () -> confirmDeleteAll(group)));
                }
            }
            for (BackupInfo backup : group) {
                String size = UiText.formatBytes(backup.sizeBytes());
                String customName = backup.customName();
                if (customName != null) {
                    // custom name big, date and size under it
                    rows.add(PickerList.Row.item(backup, Component.literal(customName),
                            Component.translatable("quickactions.backup.row.info.named", backup.dateText(), size)));
                } else {
                    rows.add(PickerList.Row.item(backup, Component.literal(backup.dateText()),
                            Component.literal(size)));
                }
            }
        }
        list.setRows(rows);

        if (select != null) {
            for (BackupInfo backup : backups) {
                if (backup.file().equals(select)) {
                    list.select(backup);
                    break;
                }
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        if (rollBackButton == null) return;
        BackupInfo selected = list.selected();
        rollBackButton.active = selected != null && worldExists(selected);
        restoreNewButton.active = selected != null;
        renameButton.active = selected != null;
        deleteButton.active = selected != null;
    }

    // ---------- actions ----------

    private void rollBackSelected() {
        BackupInfo backup = list.selected();
        if (backup == null) return;

        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        BackupOperations.rollBack(parent, refresh, backup);
                    } else {
                        mc.setScreenAndShow(this);
                    }
                },
                Component.translatable("quickactions.backup.rollback.title"),
                Component.translatable("quickactions.backup.rollback.message", backup.worldName(), backup.dateText()),
                Component.translatable("quickactions.backup.rollback.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    private void restoreSelectedAsNew() {
        BackupInfo backup = list.selected();
        if (backup == null) return;
        BackupOperations.restoreAsNew(parent, refresh, backup);
    }

    private void renameSelected() {
        BackupInfo backup = list.selected();
        if (backup == null) return;

        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                this,
                Component.translatable("quickactions.backup.rename.title"),
                backup.fileBaseName(),
                64,
                newName -> {
                    try {
                        Path renamed = BackupManager.rename(backup, newName);
                        reload(renamed);
                    } catch (Exception e) {
                        QuickActionsClient.LOGGER.error("Failed to rename backup", e);
                        Notifications.failure();
                    }
                }));
    }

    private void deleteSelected() {
        BackupInfo backup = list.selected();
        if (backup == null) return;

        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        try {
                            BackupManager.delete(backup);
                        } catch (IOException e) {
                            QuickActionsClient.LOGGER.error("Failed to delete backup", e);
                            Notifications.failure();
                        }
                    }
                    mc.setScreenAndShow(this);
                    reload(null);
                },
                Component.translatable("quickactions.backup.delete.title"),
                Component.translatable("quickactions.backup.delete.message", backup.worldName(), backup.dateText()),
                Component.translatable("quickactions.backup.delete.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    private void confirmDeleteAll(List<BackupInfo> group) {
        BackupInfo first = group.get(0);
        int count = group.size();
        long totalBytes = 0;
        for (BackupInfo backup : group) {
            totalBytes += backup.sizeBytes();
        }
        String size = UiText.formatBytes(totalBytes);

        Minecraft.getInstance().setScreenAndShow(new ChoiceScreen(
                this,
                Component.translatable("quickactions.backup.delete_all.title"),
                Component.translatable("quickactions.backup.delete_all.message", count, first.worldName(), size),
                List.of(
                        new ChoiceScreen.Choice(
                                Component.translatable("quickactions.backup.delete_all.confirm", count),
                                () -> deleteAll(first.levelId(), count, size)),
                        new ChoiceScreen.Choice(CommonComponents.GUI_CANCEL, () -> { })),
                true));
    }

    private void deleteAll(String levelId, int count, String size) {
        try {
            BackupManager.deleteAll(levelId);
            Notification.success(Component.translatable("quickactions.notify.backups_deleted.title"))
                    .message(Component.translatable("quickactions.notify.backups_deleted.message", count, size))
                    .show();
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to delete all backups of {}", levelId, e);
            Notifications.failure();
        }
        reload(null);
    }

    private void openFolder() {
        BackupInfo selected = list.selected();
        Path dir = selected != null
                ? selected.file().getParent()
                : (filterLevelId != null ? BackupManager.worldDir(filterLevelId) : BackupManager.rootDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // open what's there
        }
        ClientUtil.openPath(dir);
    }

    @Override
    public void onClose() {
        WorldIcons.clear();
        Minecraft.getInstance().setScreenAndShow(parent);
        refresh.run();
    }

    // ---------- render ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 16,
                COLOR_TEXT, true);

        String hint = UiText.fitStart(this.font, BackupManager.rootDir().toString(), LIST_WIDTH);
        graphics.text(this.font, Component.literal(hint), (this.width - this.font.width(hint)) / 2, 30,
                Theme.muted(), true);

        list.render(graphics, this.font, mouseX, mouseY);
        list.renderTooltip(graphics, this.font, mouseX, mouseY, this.width, this.height);
    }
}