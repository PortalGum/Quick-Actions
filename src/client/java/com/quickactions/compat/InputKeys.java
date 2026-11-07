package com.quickactions.compat;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * InputConstants, but read at runtime
 * javac inlines the real constants, so the jar carried 26.3's numbers onto 26.1 and clicks went to the wrong button
 * these fields are not compile-time constants, so they stay as they are
 */
public final class InputKeys {
    public static final int MOUSE_BUTTON_LEFT = read("MOUSE_BUTTON_LEFT", 0);
    public static final int MOUSE_BUTTON_RIGHT = read("MOUSE_BUTTON_RIGHT", 1);
    public static final int MOUSE_BUTTON_MIDDLE = read("MOUSE_BUTTON_MIDDLE", 2);

    public static final int KEY_ESCAPE = read("KEY_ESCAPE", 256);
    public static final int KEY_RETURN = read("KEY_RETURN", 257);
    public static final int KEY_DELETE = read("KEY_DELETE", 261);
    public static final int KEY_HOME = read("KEY_HOME", 268);
    public static final int KEY_END = read("KEY_END", 269);
    public static final int KEY_UP = read("KEY_UP", 265);
    public static final int KEY_DOWN = read("KEY_DOWN", 264);
    public static final int KEY_LEFT = read("KEY_LEFT", 263);
    public static final int KEY_RIGHT = read("KEY_RIGHT", 262);
    public static final int KEY_SPACE = read("KEY_SPACE", 32);
    public static final int KEY_MINUS = read("KEY_MINUS", 45);
    public static final int KEY_EQUALS = read("KEY_EQUALS", 61);
    public static final int KEY_0 = read("KEY_0", 48);
    public static final int KEY_LCONTROL = read("KEY_LCONTROL", 341);
    public static final int KEY_RCONTROL = read("KEY_RCONTROL", 345);

    private InputKeys() {
    }

    private static int read(String name, int fallback) {
        try {
            return InputConstants.class.getField(name).getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback; // no such field here, glfw number and hope for the best
        }
    }
}