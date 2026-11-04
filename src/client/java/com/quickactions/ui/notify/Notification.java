package com.quickactions.ui.notify;

import com.quickactions.ui.theme.Theme;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class Notification {

    public enum Type {
        SUCCESS(0xFF3FB950),
        INFO(0),
        ERROR(0xFFF85149);

        private final int fixedColor;

        Type(int color) {
            this.fixedColor = color;
        }

        int color() {
            return this == INFO ? Theme.accent() : fixedColor;
        }
    }

    final Type type;
    final Component title;
    @Nullable Component message;
    final java.util.List<Component> rows = new java.util.ArrayList<>();
    @Nullable String detail;
    @Nullable Component actionLabel;
    @Nullable Runnable action;
    long durationMs = 6000;

    // animation state
    long createdNanos = System.nanoTime();
    long pausedNanos;

    boolean drawn;
    float alpha;
    int drawX;
    int drawY;
    int drawHeight;
    int buttonX;
    int buttonY;
    int buttonWidth;

    private Notification(Type type, Component title) {
        this.type = type;
        this.title = title;
    }

    public static Notification success(Component title) {
        return new Notification(Type.SUCCESS, title);
    }

    public static Notification info(Component title) {
        return new Notification(Type.INFO, title);
    }

    public static Notification error(Component title) {
        return new Notification(Type.ERROR, title);
    }

    public Notification message(Component message) {
        this.message = message;
        return this;
    }

    public Notification row(Component row) {
        this.rows.add(row);
        return this;
    }

    public Notification detail(String detail) {
        this.detail = detail;
        return this;
    }

    public Notification action(Component label, Runnable action) {
        this.actionLabel = label;
        this.action = action;
        return this;
    }

    public Notification duration(long millis) {
        this.durationMs = millis;
        return this;
    }

    public void show() {
        NotificationManager.show(this);
    }

    long elapsedMs(long nowNanos) {
        return (nowNanos - createdNanos - pausedNanos) / 1_000_000L;
    }

    /** start fading out right now */
    void dismiss() {
        durationMs = Math.min(durationMs, elapsedMs(System.nanoTime()));
    }
}