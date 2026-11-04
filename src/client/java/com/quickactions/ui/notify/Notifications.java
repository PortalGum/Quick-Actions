package com.quickactions.ui.notify;

import com.quickactions.QuickActionsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletionException;

/** the usual toasts */
public final class Notifications {
    private Notifications() {
    }

    /**
     * reports an unexpected problem: full stack trace goes to the log + the "something went wrong, see the log" toast
     * any thread is fine
     *
     * @param what  what we were doing, e.g. "Copy screenshot to clipboard"
     * @param error the cause, can be null if there's no exception (then put the reason in {@code what})
     */
    public static void failure(String what, @Nullable Throwable error) {
        Throwable cause = unwrap(error);
        if (cause != null) {
            QuickActionsClient.LOGGER.error("{} failed", what, cause);
        } else {
            QuickActionsClient.LOGGER.error("{} failed", what);
        }
        Minecraft.getInstance().execute(Notifications::failure);
    }

    /** just the toast, caller must log the problem itself, otherwise use {@link #failure(String, Throwable)} */
    public static void failure() {
        Notification.error(Component.translatable("quickactions.notify.failed.title"))
                .message(Component.translatable("quickactions.notify.failed.message"))
                .duration(8000)
                .show();
    }

    private static @Nullable Throwable unwrap(@Nullable Throwable error) {
        Throwable t = error;
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        return t;
    }
}