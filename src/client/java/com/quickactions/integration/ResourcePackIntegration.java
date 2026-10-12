package com.quickactions.integration;

import com.quickactions.actions.pack.BuiltinPackActions;
import com.quickactions.actions.pack.BuiltinPackTarget;
import com.quickactions.actions.pack.PackActions;
import com.quickactions.actions.pack.PackListActions;
import com.quickactions.actions.pack.PackListTarget;
import com.quickactions.actions.pack.PackTarget;
import com.quickactions.mixin.client.PackEntryAccessor;
import com.quickactions.ui.ContextMenuHost;
import com.quickactions.ui.MenuItem;
import com.quickactions.ui.QuickActionMenu;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ResourcePackIntegration {
    private static final PackActions PACK_ACTIONS = new PackActions();
    private static final BuiltinPackActions BUILTIN_ACTIONS = new BuiltinPackActions();
    private static final PackListActions LIST_ACTIONS = new PackListActions();
    private static final String FILE_PREFIX = "file/";

    private ResourcePackIntegration() {
    }

    public static void tryAttach(Screen screen) {
        if (!(screen instanceof PackSelectionScreen) || !isResourcePackScreen(screen)) return;

        ContextMenuHost.install(screen, (x, y) -> itemsAt(screen, x, y));

        // 2. pack dragging
        PackDragReorder.install(screen);
    }

    private static boolean isResourcePackScreen(Screen screen) {
        String expected = Component.translatable("resourcePack.title").getString();
        return screen.getTitle().getString().equals(expected);
    }

    // ---------- screen list access ----------

    public static List<TransferableSelectionList> lists(Screen screen) {
        return screen.children().stream()
                .filter(TransferableSelectionList.class::isInstance)
                .map(TransferableSelectionList.class::cast)
                .toList();
    }

    public static List<TransferableSelectionList.PackEntry> packEntries(TransferableSelectionList list) {
        List<TransferableSelectionList.PackEntry> result = new ArrayList<>();
        for (var child : list.children()) {
            if (child instanceof TransferableSelectionList.PackEntry entry) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<TransferableSelectionList.PackEntry> allEntries(Screen screen) {
        List<TransferableSelectionList.PackEntry> result = new ArrayList<>();
        for (TransferableSelectionList list : lists(screen)) {
            result.addAll(packEntries(list));
        }
        return result;
    }

    public static PackSelectionModel.Entry modelOf(TransferableSelectionList.PackEntry entry) {
        return ((PackEntryAccessor) (Object) entry).quickactions$getPack();
    }

    // ---------- menu ----------

    private static @Nullable List<MenuItem> itemsAt(Screen screen, double x, double y) {
        if (!PACK_ACTIONS.category().isEnabled()) return null;

        for (TransferableSelectionList list : lists(screen)) {
            Object entry = list.getEntryAtPosition(x, y);
            if (entry instanceof TransferableSelectionList.PackEntry packEntry) {
                return menuForPack(screen, packEntry);
            }
        }

        for (TransferableSelectionList list : lists(screen)) {
            if (list.isMouseOver(x, y)) {
                return List.of(QuickActionMenu.build(LIST_ACTIONS, new PackListTarget(screen)));
            }
        }
        return null;
    }

    private static @Nullable List<MenuItem> menuForPack(Screen screen, TransferableSelectionList.PackEntry packEntry) {
        PackSelectionModel.Entry model = modelOf(packEntry);
        String id = packEntry.getPackId();

        if (!id.startsWith(FILE_PREFIX)) {
            if (model.isSelected() && !model.isFixedPosition()) {
                return List.of(QuickActionMenu.build(BUILTIN_ACTIONS, new BuiltinPackTarget(model)));
            }
            return null;
        }

        String fileName = id.substring(FILE_PREFIX.length());
        Path path = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(fileName);
        if (!Files.exists(path)) return null;

        boolean active = model.isSelected();
        boolean incompatible = !model.getCompatibility().isCompatible();
        PackTarget target = new PackTarget(id, fileName, path, model, active, incompatible, screen);
        return List.of(QuickActionMenu.build(PACK_ACTIONS, target));
    }
}