package com.quickactions.actions.screenshot;

import com.quickactions.api.ActionCategory;
import com.quickactions.api.ActionEntry;
import com.quickactions.api.ActionProvider;
import com.quickactions.api.QuickAction;
import com.quickactions.api.QuickActionGroup;
import com.quickactions.api.SimpleQuickAction;

import java.util.List;

public final class ScreenshotActions implements ActionProvider<ScreenshotTarget> {

    private final List<ActionEntry<ScreenshotTarget>> entries = List.<ActionEntry<ScreenshotTarget>>of(
            new SimpleQuickAction<ScreenshotTarget>(
                    "screenshot.show_in_folder",
                    ScreenshotTarget::exists,
                    ScreenshotOperations::showInFolder),

            new SimpleQuickAction<ScreenshotTarget>(
                    "screenshot.save_as",
                    ScreenshotTarget::exists,
                    ScreenshotOperations::saveAs),

            new SimpleQuickAction<ScreenshotTarget>(
                    "screenshot.copy_image",
                    ScreenshotTarget::exists,
                    ScreenshotOperations::copyImage),

            new SimpleQuickAction<ScreenshotTarget>(
                    "screenshot.copy_path",
                    ScreenshotTarget::exists,
                    ScreenshotOperations::copyPath),

            new QuickActionGroup<ScreenshotTarget>("screenshot.edit", List.<QuickAction<ScreenshotTarget>>of(
                    new SimpleQuickAction<ScreenshotTarget>(
                            "screenshot.edit.rename",
                            ScreenshotTarget::exists,
                            ScreenshotOperations::rename))),

            new SimpleQuickAction<ScreenshotTarget>(
                    "screenshot.delete",
                    ScreenshotTarget::exists,
                    ScreenshotOperations::delete)
    );

    @Override
    public ActionCategory category() {
        return ActionCategory.SCREENSHOT;
    }

    @Override
    public List<ActionEntry<ScreenshotTarget>> entries() {
        return entries;
    }
}
