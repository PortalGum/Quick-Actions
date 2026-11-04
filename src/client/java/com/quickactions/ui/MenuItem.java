package com.quickactions.ui;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

public record MenuItem(
        Component label,
        boolean enabled,
        @Nullable Runnable action,
        List<MenuItem> children,
        @Nullable BooleanSupplier checked,
        @Nullable Component hint) {

    public static MenuItem action(Component label, boolean enabled, Runnable action) {
        return new MenuItem(label, enabled, action, List.of(), null, null);
    }

    public static MenuItem toggle(Component label, boolean enabled, BooleanSupplier checked, Runnable action) {
        return new MenuItem(label, enabled, action, List.of(), checked, null);
    }

    public static MenuItem submenu(Component label, List<MenuItem> children) {
        return submenu(label, children, null);
    }

    public static MenuItem submenu(Component label, List<MenuItem> children, @Nullable Component hint) {
        return new MenuItem(label, true, null, children, null, hint);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean keepsMenuOpen() {
        return checked != null;
    }
}