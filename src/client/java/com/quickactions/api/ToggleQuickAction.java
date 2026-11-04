package com.quickactions.api;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ToggleQuickAction<T> implements QuickAction<T> {
    private final String id;
    private final Predicate<T> available;
    private final Predicate<T> state;
    private final Consumer<T> toggle;

    public ToggleQuickAction(String id, Predicate<T> available, Predicate<T> state, Consumer<T> toggle) {
        this.id = id;
        this.available = available;
        this.state = state;
        this.toggle = toggle;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Component name() {
        return Component.translatable("quickactions.action." + id);
    }

    @Override
    public Component description() {
        return Component.translatable("quickactions.action." + id + ".desc");
    }

    @Override
    public Boolean checked(T target) {
        return state.test(target);
    }

    @Override
    public boolean isAvailable(T target) {
        return available.test(target);
    }

    @Override
    public void run(T target) {
        toggle.accept(target);
    }
}