package com.quickactions.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quickactions.QuickActionsClient;
import com.quickactions.config.QuickActionsConfig;
import com.quickactions.ui.notify.Notification;
import com.quickactions.util.ClientUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public final class UpdateChecker {
    private static final String PROJECT_ID = "T1rCeI8G";
    private static final String API = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version";
    private static final String PAGE = "https://modrinth.com/project/" + PROJECT_ID + "/version/";
    private static final long NOTIFICATION_MS = 14_000;

    private record Found(String current, String latest, String url) {
    }

    private static volatile Found found;
    private static boolean started;
    private static boolean shown;

    private UpdateChecker() {
    }

    public static void start() {
        if (started || !QuickActionsConfig.get().checkForUpdates) return;
        started = true;

        String currentRaw = FabricLoader.getInstance().getModContainer(QuickActionsClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
        ModVersion current = ModVersion.parse(currentRaw);
        if (current == null) {
            QuickActionsClient.LOGGER.info("Update check skipped: unknown mod version '{}'", currentRaw);
            return;
        }
        String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);

        Thread thread = new Thread(() -> check(currentRaw, current, minecraft), "QuickActions-UpdateCheck");
        thread.setDaemon(true);
        thread.start();
    }

    private static void check(String currentRaw, ModVersion current, String minecraft) {
        try {
            StringBuilder url = new StringBuilder(API).append("?loaders=")
                    .append(encode("[\"fabric\"]"));
            if (minecraft != null) {
                url.append("&game_versions=").append(encode("[\"" + minecraft + "\"]"));
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "PortalGum/Quick-Actions/" + currentRaw + " (modrinth.com/project/" + PROJECT_ID + ")")
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                QuickActionsClient.LOGGER.info("Update check: Modrinth answered {}", response.statusCode());
                return;
            }

            ModVersion best = null;
            String bestRaw = null;
            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            for (JsonElement element : versions) {
                JsonObject v = element.getAsJsonObject();
                if (!"listed".equals(string(v, "status", "listed"))) continue;
                String type = string(v, "version_type", "release");
                if (!type.equals("release")) continue; // offer releases only, skip beta and alpha
                String number = string(v, "version_number", null);
                ModVersion parsed = ModVersion.parse(number);
                if (parsed == null) continue;
                if (best == null || parsed.isNewerThan(best)) {
                    best = parsed;
                    bestRaw = number;
                }
            }

            if (best != null && best.isNewerThan(current)) {
                found = new Found(currentRaw, bestRaw, PAGE + encodePath(bestRaw));
                QuickActionsClient.LOGGER.info("Update available: {} -> {}", currentRaw, bestRaw);
            } else {
                QuickActionsClient.LOGGER.info("Quick Actions is up to date ({})", currentRaw);
            }
        } catch (Exception e) {
            QuickActionsClient.LOGGER.info("Update check failed: {}", e.toString());
        }
    }

    public static void onScreenInit(Screen screen) {
        Found update = found;
        if (update == null || shown || !QuickActionsConfig.get().checkForUpdates) return;
        if (screen instanceof TitleScreen || isTransient(screen)) return;
        shown = true;

        Component current = Component.literal(update.current()).withStyle(ChatFormatting.RED);
        Component latest = Component.literal(update.latest()).withStyle(ChatFormatting.GREEN);

        Notification.info(Component.translatable("quickactions.update.title"))
                .row(Component.translatable("quickactions.update.current", current))
                .row(Component.translatable("quickactions.update.latest", latest))
                .action(Component.translatable("quickactions.update.button"), () -> ClientUtil.openUrl(update.url()))
                .duration(NOTIFICATION_MS)
                .show();
    }

    private static boolean isTransient(Screen screen) {
        String name = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("loading") || name.contains("progress") || name.contains("connect")
                || name.contains("receiving") || name.contains("genericmessage") || name.contains("reconfiguration");
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement e = object.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%2B");
    }
}
