package com.quickactions.actions.server;

import com.quickactions.api.ActionCategory;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.QuickAction;
import com.quickactions.api.QuickActionGroup;
import com.quickactions.api.SimpleQuickAction;
import com.quickactions.api.ToggleQuickAction;
import com.quickactions.config.ServerHideStore;
import com.quickactions.config.ServerHideStore.HideField;

import java.util.List;

/** common actions for the whole server list */
public final class ServerListActions implements ActionProvider<ServerListTarget> {

    private final List<ActionEntry<ServerListTarget>> entries = List.<ActionEntry<ServerListTarget>>of(
            new SimpleQuickAction<ServerListTarget>(
                    "server.global.refresh",
                    target -> true,
                    ServerOperations::refreshAll),

            new QuickActionGroup<ServerListTarget>("server.global.hide", List.<QuickAction<ServerListTarget>>of(
                    globalToggle("server.global.hide.motd", HideField.MOTD),
                    globalToggle("server.global.hide.online", HideField.ONLINE),
                    globalToggle("server.global.hide.icon", HideField.ICON))),

            new SimpleQuickAction<ServerListTarget>(
                    "server.global.export",
                    target -> target.servers().size() > 0, // nothing to export while list is empty
                    ServerListOperations::exportList),

            new SimpleQuickAction<ServerListTarget>(
                    "server.global.import",
                    target -> true,
                    ServerListOperations::openImport),

            new SimpleQuickAction<ServerListTarget>(
                    "server.global.clear",
                    target -> target.servers().size() > 0, // nothing to clear while list is empty
                    ServerListOperations::openClearConfirm)
    );

    private static ToggleQuickAction<ServerListTarget> globalToggle(String id, HideField field) {
        return new ToggleQuickAction<ServerListTarget>(
                id,
                target -> true,
                target -> ServerHideStore.isGlobal(field),
                target -> ServerHideStore.toggleGlobal(field));
    }

    @Override
    public ActionCategory category() {
        return ActionCategory.SERVER;
    }

    @Override
    public List<ActionEntry<ServerListTarget>> entries() {
        return entries;
    }
}