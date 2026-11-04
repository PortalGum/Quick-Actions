package com.quickactions.integration;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.world.WorldActions;
import com.quickactions.actions.world.WorldListActions;
import com.quickactions.actions.world.WorldListTarget;
import com.quickactions.actions.world.WorldTarget;
import com.quickactions.mixin.client.WorldListEntryAccessor;
import com.quickactions.ui.ContextMenuHost;
import com.quickactions.ui.MenuItem;
import com.quickactions.ui.QuickActionMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WorldSelectionIntegration {
    private static final WorldActions WORLD_ACTIONS = new WorldActions();
    private static final WorldListActions LIST_ACTIONS = new WorldListActions();

    private WorldSelectionIntegration() {
    }

    public static void tryAttach(Screen screen) {
        if (!(screen instanceof SelectWorldScreen)) return;

        if (findList(screen) == null) {
            QuickActionsClient.LOGGER.warn("WorldSelectionList not found on SelectWorldScreen");
            return;
        }

        ContextMenuHost.install(screen, (x, y) -> itemsAt(screen, x, y));
    }

    private static @Nullable WorldSelectionList findList(Screen screen) {
        return screen.children().stream()
                .filter(WorldSelectionList.class::isInstance)
                .map(WorldSelectionList.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static @Nullable List<MenuItem> itemsAt(Screen screen, double x, double y) {
        if (!WORLD_ACTIONS.category().isEnabled()) return null;

        WorldSelectionList list = findList(screen);
        if (list == null) return null;

        Runnable refresh = () -> {
            WorldSelectionList current = findList(screen);
            if (current != null) {
                current.reloadWorldList();
            }
        };

        Object entry = list.getEntryAtPosition(x, y);

        // rmb on world: that world's menu
        if (entry instanceof WorldSelectionList.WorldListEntry worldEntry) {
            LevelSummary summary = ((WorldListEntryAccessor) (Object) worldEntry).quickactions$getSummary();
            WorldTarget target = new WorldTarget(
                    summary.getLevelId(),
                    summary.getLevelName(),
                    screen,
                    refresh,
                    worldEntry);
            return List.of(QuickActionMenu.build(WORLD_ACTIONS, target));
        }

        // rmb on empty space: common actions
        if (list.isMouseOver(x, y)) {
            WorldListTarget target = new WorldListTarget(screen, refresh);
            return List.of(QuickActionMenu.build(LIST_ACTIONS, target));
        }

        return null;
    }
}