package com.quickactions.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public interface QuickAction<T> extends ActionEntry<T> {
    @Override
    String id();

    Component name();

    Component description();

    /** action icon (not drawn yet, later) */
    default @Nullable Identifier icon() {
        return null;
    }

    default @Nullable Boolean checked(T target) {
        return null;
    }

    boolean isAvailable(T target);

    void run(T target);
}