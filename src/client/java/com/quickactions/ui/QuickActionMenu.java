package com.quickactions.ui;

import com.quickactions.QuickActionsClient;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.QuickAction;
import com.quickactions.api.QuickActionGroup;
import com.quickactions.util.ClientUtil;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class QuickActionMenu {
    private QuickActionMenu() {
    }

    public static <T> MenuItem build(ActionProvider<T> provider, T target) {
        return MenuItem.submenu(
                Component.translatable("quickactions.menu.title"),
                toItems(provider.entries(), target));
    }

    private static <T> List<MenuItem> toItems(List<? extends ActionEntry<T>> entries, T target) {
        List<MenuItem> items = new ArrayList<>();
        for (ActionEntry<T> entry : entries) {
            if (entry instanceof QuickActionGroup<T> group) {
                items.add(MenuItem.submenu(group.name(), toItems(group.actions(), target), hintFor(group.id())));
            } else if (entry instanceof QuickAction<T> action) {
                BooleanSupplier checked = action.checked(target) == null
                        ? null
                        : () -> Boolean.TRUE.equals(action.checked(target));
                items.add(new MenuItem(
                        action.name(),
                        action.isAvailable(target),
                        () -> runSafely(action, target),
                        List.of(),
                        checked,
                        hintFor(action.id())));
            }
        }
        return items;
    }

    private static @Nullable Component hintFor(String id) {
        String key = "quickactions.action." + id + ".hint";
        Component hint = Component.translatable(key);
        return hint.getString().equals(key) ? null : hint;
    }

    private static <T> void runSafely(QuickAction<T> action, T target) {
        try {
            action.run(target);
        } catch (Exception e) {
            QuickActionsClient.LOGGER.error("Quick action '{}' failed", action.id(), e);
            ClientUtil.fail();
        }
    }
}