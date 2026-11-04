package com.quickactions;

import com.quickactions.config.QuickActionsConfig;
import com.quickactions.config.ServerHideStore;
import com.quickactions.integration.ResourcePackIntegration;
import com.quickactions.integration.ServerListIntegration;
import com.quickactions.integration.TitleScreenIntegration;
import com.quickactions.integration.WorldSelectionIntegration;
import com.quickactions.ui.notify.NotificationManager;
import com.quickactions.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuickActionsClient implements ClientModInitializer {
    public static final String MOD_ID = "quickactions";
    public static final Logger LOGGER = LoggerFactory.getLogger("Quick Actions");

    @Override
    public void onInitializeClient() {
        QuickActionsConfig.load();
        ServerHideStore.load();
        UpdateChecker.start();

        // runs on every (re)init of any screen
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            NotificationManager.install(screen);
            WorldSelectionIntegration.tryAttach(screen);
            ServerListIntegration.tryAttach(screen);
            ResourcePackIntegration.tryAttach(screen);
            TitleScreenIntegration.tryAttach(screen);
            UpdateChecker.onScreenInit(screen);
        });

        LOGGER.info("Quick Actions loaded");
    }
}