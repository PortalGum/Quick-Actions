package com.quickactions.ui;

import com.quickactions.compat.InputKeys;

// don't go back to InputConstants directly, see InputKeys
public final class MouseButtons {
    public static final int LEFT = InputKeys.MOUSE_BUTTON_LEFT;
    public static final int RIGHT = InputKeys.MOUSE_BUTTON_RIGHT;
    public static final int MIDDLE = InputKeys.MOUSE_BUTTON_MIDDLE;

    private MouseButtons() {
    }
}