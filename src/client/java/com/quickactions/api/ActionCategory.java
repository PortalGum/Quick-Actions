package com.quickactions.api;

import com.quickactions.config.QuickActionsConfig;

import java.util.function.Predicate;

public enum ActionCategory {
    WORLD(c -> c.showInWorldSelection),
    SERVER(c -> c.showInServerList),
    RESOURCE_PACK(c -> c.showInResourcePacks),
    SCREENSHOT(c -> c.showInScreenshotViewer);

    private final Predicate<QuickActionsConfig> flag;

    ActionCategory(Predicate<QuickActionsConfig> flag) {
        this.flag = flag;
    }

    public boolean isEnabled() {
        QuickActionsConfig config = QuickActionsConfig.get();
        return config.enabled && flag.test(config);
    }
}