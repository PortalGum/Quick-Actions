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

public final class ServerActions implements ActionProvider<ServerTarget> {

    private final List<ActionEntry<ServerTarget>> entries = List.<ActionEntry<ServerTarget>>of(
            new SimpleQuickAction<ServerTarget>(
                    "server.refresh",
                    target -> true,
                    ServerOperations::refresh),

            new SimpleQuickAction<ServerTarget>(
                    "server.copy_ip",
                    target -> true,
                    ServerOperations::copyIp),

            new QuickActionGroup<ServerTarget>("server.edit", List.<QuickAction<ServerTarget>>of(
                    new SimpleQuickAction<ServerTarget>(
                            "server.edit.rename",
                            target -> true,
                            ServerOperations::rename),
                    new SimpleQuickAction<ServerTarget>(
                            "server.edit.address",
                            target -> true,
                            ServerOperations::changeAddress))),

            new QuickActionGroup<ServerTarget>("server.hide", List.<QuickAction<ServerTarget>>of(
                    hideToggle("server.hide.motd", HideField.MOTD),
                    hideToggle("server.hide.online", HideField.ONLINE),
                    hideToggle("server.hide.icon", HideField.ICON))),

            new SimpleQuickAction<ServerTarget>(
                    "server.delete",
                    target -> true,
                    ServerOperations::delete)
    );

    private static ToggleQuickAction<ServerTarget> hideToggle(String id, HideField field) {
        return new ToggleQuickAction<ServerTarget>(
                id,
                target -> true,
                target -> ServerHideStore.isHidden(target.address(), field),
                target -> ServerHideStore.toggle(target.address(), field));
    }

    @Override
    public ActionCategory category() {
        return ActionCategory.SERVER;
    }

    @Override
    public List<ActionEntry<ServerTarget>> entries() {
        return entries;
    }
}