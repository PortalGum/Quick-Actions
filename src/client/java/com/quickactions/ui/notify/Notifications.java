package com.quickactions.ui.notify;

import net.minecraft.network.chat.Component;

/** ready-made common notifications */
public final class Notifications {
    private Notifications() {
    }

    public static void failure() {
        Notification.error(Component.translatable("quickactions.notify.failed.title"))
                .message(Component.translatable("quickactions.notify.failed.message"))
                .duration(8000)
                .show();
    }
}