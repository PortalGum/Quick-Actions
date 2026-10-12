package com.quickactions.api;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SimpleQuickAction<T> implements QuickAction<T> {
    private final String id;
    private final Predicate<T> available;
    private final Consumer<T> handler;

    public SimpleQuickAction(String id, Predicate<T> available, Consumer<T> handler) {
        this.id = id;
        this.available = available;
        this.handler = handler;
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
    public boolean isAvailable(T target) {
        return available.test(target);
    }

    @Override
    public void run(T target) {
        handler.accept(target);
    }
}