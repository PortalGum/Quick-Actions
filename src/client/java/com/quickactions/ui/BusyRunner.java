package com.quickactions.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class BusyRunner {
    private BusyRunner() {
    }

    public static <R> void run(Screen returnTo, Component title, Callable<R> work,
                               Consumer<R> onSuccess, Consumer<Throwable> onError) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new BusyScreen(title, Component.translatable("quickactions.busy.message")));

        CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((result, error) -> mc.execute(() -> {
            mc.setScreenAndShow(returnTo);
            if (error != null) {
                onError.accept(error.getCause() != null ? error.getCause() : error);
            } else {
                onSuccess.accept(result);
            }
        }));
    }
}