package com.quickactions.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ContextMenuHost {

    @FunctionalInterface
    public interface Provider {
        @Nullable List<MenuItem> itemsAt(double x, double y);
    }

    private final Screen screen;
    private final Provider provider;
    private @Nullable ContextMenu menu;

    private ContextMenuHost(Screen screen, Provider provider) {
        this.screen = screen;
        this.provider = provider;
    }

    public static void install(Screen screen, Provider provider) {
        new ContextMenuHost(screen, provider).register();
    }

    private void register() {
        ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
            if (menu != null) {
                menu.extract(graphics, mouseX, mouseY);
            }
        });

        ScreenMouseEvents.allowMouseClick(screen).register(this::onMouseClick);

        ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
            menu = null;
            return true;
        });

        ScreenKeyboardEvents.allowKeyPress(screen).register(this::onKeyPress);
    }

    private boolean onMouseClick(Screen s, MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        int button = event.button();

        if (menu != null) {
            if (!menu.isInside(x, y)) {
                menu = null;
                return false;
            }
            if (menu.isOverHint(x, y)) {
                return false; // click on (?) icon runs nothing
            }

            if (button == MouseButtons.LEFT) {
                MenuItem item = menu.itemAt(x, y);
                if (item != null && item.enabled() && item.action() != null) {
                    if (!item.keepsMenuOpen()) {
                        menu = null;
                    }
                    item.action().run();
                }
            }
            return false;
        }

        if (button == MouseButtons.RIGHT) {
            List<MenuItem> items = provider.itemsAt(x, y);
            if (items != null && !items.isEmpty()) {
                menu = new ContextMenu(screen, items, (int) x, (int) y);
                return false;
            }
        }
        return true;
    }

    private boolean onKeyPress(Screen s, KeyEvent event) {
        if (menu != null && event.key() == InputConstants.KEY_ESCAPE) {
            menu = null;
            return false;
        }
        return true;
    }
}