package com.quickactions.actions.server;

import com.quickactions.ui.theme.Theme;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.server.ServerListTransfer.Entry;
import com.quickactions.actions.server.ServerListTransfer.ImportMode;
import com.quickactions.actions.server.ServerListTransfer.ListFile;
import com.quickactions.ui.PickerList;
import com.quickactions.ui.TextInputScreen;
import com.quickactions.ui.UiText;
import com.quickactions.util.ClientUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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

public class ImportServerListScreen extends Screen {
    private static final int LIST_TOP = 52;
    private static final int LIST_WIDTH = 320;
    private static final int FOOTER = 84;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Screen parent;
    private final ServerListTarget target;
    private final PickerList<ListFile> list = new PickerList<>(file -> updateButtons());

    private Button addButton;
    private Button replaceButton;
    private Button renameButton;
    private Button deleteButton;

    public ImportServerListScreen(Screen parent, ServerListTarget target) {
        super(Component.translatable("quickactions.import.title"));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        list.layout(this.width / 2 - LIST_WIDTH / 2, LIST_TOP, LIST_WIDTH,
                Math.max(60, this.height - FOOTER - LIST_TOP));
        list.setEmptyText(Component.translatable("quickactions.import.empty"),
                Component.translatable("quickactions.import.empty.hint"));

        int buttonWidth = 156;
        int left = this.width / 2 - buttonWidth - 2;
        int right = this.width / 2 + 2;
        int row1 = this.height - 76;
        int row2 = this.height - 52;
        int row3 = this.height - 28;

        addButton = Button.builder(Component.translatable("quickactions.import.add"),
                        button -> importSelected(ImportMode.ADD))
                .bounds(left, row1, buttonWidth, 20).build();
        replaceButton = Button.builder(Component.translatable("quickactions.import.replace"),
                        button -> importSelected(ImportMode.REPLACE))
                .bounds(right, row1, buttonWidth, 20).build();
        renameButton = Button.builder(Component.translatable("quickactions.import.rename"),
                        button -> renameSelected())
                .bounds(left, row2, buttonWidth, 20).build();
        deleteButton = Button.builder(Component.translatable("quickactions.import.delete"),
                        button -> deleteSelected())
                .bounds(right, row2, buttonWidth, 20).build();
        addRenderableWidget(addButton);
        addRenderableWidget(replaceButton);
        addRenderableWidget(renameButton);
        addRenderableWidget(deleteButton);

        addRenderableWidget(Button.builder(Component.translatable("quickactions.import.open_folder"),
                        button -> openFolder())
                .bounds(left, row3, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(right, row3, buttonWidth, 20).build());

        reloadFiles(null);

        ScreenMouseEvents.allowMouseClick(this).register(
                (s, event) -> !list.mouseClicked(event.x(), event.y(), event.button()));
        ScreenMouseEvents.allowMouseScroll(this).register(
                (s, mouseX, mouseY, horizontal, vertical) -> !list.mouseScrolled(mouseX, mouseY, vertical));
    }

    // ---------- state ----------

    private void reloadFiles(@Nullable Path select) {
        List<ListFile> files = ServerListTransfer.listFiles();
        List<PickerList.Row<ListFile>> rows = new ArrayList<>();
        for (ListFile file : files) {
            Component info = file.serverCount() > 0
                    ? Component.translatable("quickactions.import.row.info", file.serverCount(), format(file))
                    : Component.translatable("quickactions.import.row.invalid");
            rows.add(PickerList.Row.item(file, Component.literal(file.displayName()), info));
        }
        list.setRows(rows);

        if (select != null) {
            for (ListFile file : files) {
                if (file.path().equals(select)) {
                    list.select(file);
                    break;
                }
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        if (addButton == null) return;
        ListFile file = list.selected();
        boolean importable = file != null && file.serverCount() > 0;
        addButton.active = importable;
        replaceButton.active = importable;
        renameButton.active = file != null;
        deleteButton.active = file != null;
    }

    private static String format(ListFile file) {
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.modifiedMillis()), ZoneId.systemDefault());
        return DATE_FORMAT.format(time);
    }

    // ---------- actions ----------

    private void openFolder() {
        Path dir = ServerListTransfer.directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // open what's there
        }
        ClientUtil.openPath(dir);
    }

    private void renameSelected() {
        ListFile file = list.selected();
        if (file == null) return;

        Minecraft.getInstance().setScreenAndShow(new TextInputScreen(
                this,
                Component.translatable("quickactions.import.rename.title"),
                file.displayName(),
                64,
                newName -> {
                    try {
                        Path renamed = ServerListTransfer.rename(file.path(), newName);
                        reloadFiles(renamed);
                    } catch (Exception e) {
                        QuickActionsClient.LOGGER.error("Failed to rename server list", e);
                        ServerListOperations.showFailure();
                    }
                }));
    }

    private void deleteSelected() {
        ListFile file = list.selected();
        if (file == null) return;

        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        try {
                            ServerListTransfer.delete(file.path());
                        } catch (Exception e) {
                            QuickActionsClient.LOGGER.error("Failed to delete server list", e);
                            ServerListOperations.showFailure();
                        }
                    }
                    mc.setScreenAndShow(this);
                    reloadFiles(null);
                },
                Component.translatable("quickactions.import.delete.title"),
                Component.translatable("quickactions.import.delete.message", file.displayName()),
                Component.translatable("quickactions.import.delete.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    private void importSelected(ImportMode mode) {
        ListFile file = list.selected();
        if (file == null || file.serverCount() <= 0) return;

        List<Entry> entries;
        try {
            entries = ServerListTransfer.read(file.path());
        } catch (IOException e) {
            ServerListOperations.showFailure();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mode == ImportMode.REPLACE && target.servers().size() > 0) {
            int current = target.servers().size();
            int incoming = entries.size();
            List<Entry> toImport = entries;
            mc.setScreenAndShow(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            finishImport(toImport, mode);
                        } else {
                            mc.setScreenAndShow(this);
                        }
                    },
                    Component.translatable("quickactions.import.replace.title"),
                    Component.translatable("quickactions.import.replace.message", current, incoming),
                    Component.translatable("quickactions.import.replace.confirm"),
                    CommonComponents.GUI_CANCEL));
        } else {
            finishImport(entries, mode);
        }
    }

    private void finishImport(List<Entry> entries, ImportMode mode) {
        ServerListOperations.performImport(target, entries, mode);
        Minecraft.getInstance().setScreenAndShow(parent);
        target.updateList().run(); // refresh list on the recreated screen
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    // ---------- render ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 16,
                COLOR_TEXT, true);

        String hint = UiText.fitStart(this.font, ServerListTransfer.directory().toString(), LIST_WIDTH);
        graphics.text(this.font, Component.literal(hint), (this.width - this.font.width(hint)) / 2, 30,
                Theme.muted(), true);

        list.render(graphics, this.font, mouseX, mouseY);
    }
}