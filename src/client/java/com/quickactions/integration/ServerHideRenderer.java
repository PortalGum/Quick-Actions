package com.quickactions.integration;

import com.quickactions.api.ActionCategory;
import com.quickactions.config.ServerHideStore;
import com.quickactions.config.ServerHideStore.HideField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ServerHideRenderer {
    private static final Component BLANK = Component.literal("");
    private static final List<Component> NO_PLAYERS = new ArrayList<>();

    private record Saved(
            ServerData data,
            Component motd,
            Component status,
            List<Component> playerList,
            byte[] icon,
            boolean hidMotd,
            boolean hidOnline,
            boolean hidIcon) {
    }

    private final List<Saved> saved = new ArrayList<>();

    public void apply(ServerList servers) {
        saved.clear();
        if (!ActionCategory.SERVER.isEnabled()) return;

        for (int i = 0; i < servers.size(); i++) {
            ServerData data = servers.get(i);
            int mask = ServerHideStore.hiddenMask(data.ip);
            if (mask == 0) continue;

            boolean hideMotd = (mask & HideField.MOTD.mask()) != 0;
            boolean hideOnline = (mask & HideField.ONLINE.mask()) != 0;
            boolean hideIcon = (mask & HideField.ICON.mask()) != 0;

            saved.add(new Saved(data, data.motd, data.status, data.playerList, data.getIconBytes(),
                    hideMotd, hideOnline, hideIcon));

            if (hideMotd) {
                data.motd = BLANK;
            }
            if (hideOnline) {
                data.status = BLANK;
                data.playerList = NO_PLAYERS; // player list tooltip is hidden too
            }
            if (hideIcon) {
                data.setIconBytes(null);
            }
        }
    }

    public void restore() {
        for (Saved s : saved) {
            ServerData data = s.data();
            if (s.hidMotd() && data.motd == BLANK) {
                data.motd = s.motd();
            }
            if (s.hidOnline()) {
                if (data.status == BLANK) data.status = s.status();
                if (data.playerList == NO_PLAYERS) data.playerList = s.playerList();
            }
            if (s.hidIcon() && data.getIconBytes() == null) {
                data.setIconBytes(s.icon());
            }
        }
        saved.clear();
    }
}