package com.quickactions.integration;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.server.ServerActions;
import com.quickactions.actions.server.ServerListActions;
import com.quickactions.actions.server.ServerListTarget;
import com.quickactions.actions.server.ServerTarget;
import com.quickactions.ui.ContextMenuHost;
import com.quickactions.ui.MenuItem;
import com.quickactions.ui.QuickActionMenu;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ServerListIntegration {
    private static final ServerActions SERVER_ACTIONS = new ServerActions();
    private static final ServerListActions LIST_ACTIONS = new ServerListActions();

    private ServerListIntegration() {
    }

    public static void tryAttach(Screen screen) {
        if (!(screen instanceof JoinMultiplayerScreen multiplayerScreen)) return;

        if (findList(screen) == null) {
            QuickActionsClient.LOGGER.warn("ServerSelectionList not found on JoinMultiplayerScreen");
            return;
        }

        ContextMenuHost.install(screen, (x, y) -> itemsAt(multiplayerScreen, x, y));

        // 2. server dragging
        ServerDragReorder.install(multiplayerScreen);

        ServerHideRenderer hideRenderer = new ServerHideRenderer();
        ScreenEvents.beforeExtract(screen).register(
                (s, graphics, mouseX, mouseY, delta) -> hideRenderer.apply(multiplayerScreen.getServers()));
        ScreenEvents.afterExtract(screen).register(
                (s, graphics, mouseX, mouseY, delta) -> hideRenderer.restore());
    }

    static @Nullable ServerSelectionList findList(Screen screen) {
        return screen.children().stream()
                .filter(ServerSelectionList.class::isInstance)
                .map(ServerSelectionList.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static @Nullable List<MenuItem> itemsAt(JoinMultiplayerScreen screen, double x, double y) {
        if (!SERVER_ACTIONS.category().isEnabled()) return null;

        ServerSelectionList list = findList(screen);
        if (list == null) return null;

        Runnable updateList = () -> {
            ServerSelectionList current = findList(screen);
            if (current != null) {
                current.updateOnlineServers(screen.getServers());
            }
        };

        Object entry = list.getEntryAtPosition(x, y);

        // rmb on saved server: that server's menu
        if (entry instanceof ServerSelectionList.OnlineServerEntry serverEntry) {
            ServerTarget target = new ServerTarget(
                    serverEntry.getServerData(), screen.getServers(), screen, updateList);
            return List.of(QuickActionMenu.build(SERVER_ACTIONS, target));
        }

        if (list.isMouseOver(x, y)) {
            ServerListTarget target = new ServerListTarget(screen.getServers(), screen, updateList);
            return List.of(QuickActionMenu.build(LIST_ACTIONS, target));
        }

        return null; // bottom buttons and the rest: don't touch
    }
}