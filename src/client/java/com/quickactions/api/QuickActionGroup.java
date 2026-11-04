package com.quickactions.api;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class QuickActionGroup<T> implements ActionEntry<T> {
    private final String id;
    private final List<QuickAction<T>> actions;

    public QuickActionGroup(String id, List<QuickAction<T>> actions) {
        this.id = id;
        this.actions = actions;
    }

    @Override
    public String id() {
        return id;
    }

    public Component name() {
        return Component.translatable("quickactions.action." + id);
    }

    public List<QuickAction<T>> actions() {
        return actions;
    }
}