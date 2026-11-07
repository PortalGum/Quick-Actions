package com.quickactions.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Platform {
    private static final Logger LOGGER = LoggerFactory.getLogger("quickactions");
    private static PlatformApi instance;

    private Platform() {
    }

    public static synchronized PlatformApi get() {
        if (instance == null) instance = create();
        return instance;
    }

    private static PlatformApi create() {
        // check for the class itself, not the version number: works even if 26.2 turns out different
        boolean sdl = hasClass("org.lwjgl.sdl.SDLDialog");
        String impl = sdl
                ? "com.quickactions.compat.sdl.SdlPlatform"
                : "com.quickactions.compat.legacy.LegacyPlatform";
        try {
            // by name on purpose: a direct "new SdlPlatform()" could make the jvm load SDL classes on old versions
            PlatformApi api = (PlatformApi) Class.forName(impl).getDeclaredConstructor().newInstance();
            LOGGER.info("Quick Actions platform: {}", sdl ? "SDL (26.3+)" : "legacy (26.1/26.2)");
            return api;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Quick Actions: can't create platform implementation " + impl, e);
        }
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name, false, Platform.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}