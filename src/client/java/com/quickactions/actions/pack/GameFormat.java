package com.quickactions.actions.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class GameFormat {

    public record Version(int major, int minor) {
    }

    private GameFormat() {
    }

    public static Version currentResourcePackFormat() throws IOException {
        try (InputStream in = Minecraft.class.getResourceAsStream("/version.json")) {
            if (in == null) throw new IOException("version.json not found");

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonElement packVersion = root.get("pack_version");
            if (packVersion == null) throw new IOException("pack_version missing");

            if (packVersion.isJsonPrimitive()) { // very old format: single number
                return new Version(packVersion.getAsInt(), 0);
            }

            JsonObject object = packVersion.getAsJsonObject();
            if (object.has("resource_major")) { // 1.21.9+
                int minor = object.has("resource_minor") ? object.get("resource_minor").getAsInt() : 0;
                return new Version(object.get("resource_major").getAsInt(), minor);
            }
            if (object.has("resource")) { // before 1.21.9
                return new Version(object.get("resource").getAsInt(), 0);
            }
            throw new IOException("Unknown pack_version format");
        } catch (RuntimeException e) {
            throw new IOException("Cannot read version.json", e);
        }
    }
}