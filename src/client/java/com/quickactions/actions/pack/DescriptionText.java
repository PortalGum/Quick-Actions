package com.quickactions.actions.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.quickactions.ui.RichText;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class DescriptionText {
    private static final char SECTION = '\u00A7';

    private static final List<String> UNSUPPORTED_KEYS = List.of(
            "clickEvent", "hoverEvent", "click_event", "hover_event", "insertion", "font", "shadow_color",
            "translate", "with", "keybind", "score", "selector", "nbt");

    public record Flattened(String editable, boolean lossy) {
    }

    private record Style(@Nullable String color, boolean bold, boolean italic, boolean underlined,
                         boolean strikethrough, boolean obfuscated) {
    }

    private DescriptionText() {
    }

    // ---------- description -> editor text ----------

    public static Flattened flatten(@Nullable JsonElement description) {
        StringBuilder out = new StringBuilder();
        boolean[] lossy = {false};
        String[] emitted = {""};

        if (description != null) {
            try {
                walk(description, new Style(null, false, false, false, false, false), out, lossy, emitted);
            } catch (RuntimeException e) {
                lossy[0] = true;
            }
        }
        return new Flattened(out.toString(), lossy[0]);
    }

    private static void walk(JsonElement element, Style inherited, StringBuilder out, boolean[] lossy,
                             String[] emitted) {
        if (element.isJsonNull()) return;

        if (element.isJsonPrimitive()) {
            emit(element.getAsString(), inherited, out, emitted);
            return;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) return;
            Style base = array.get(0).isJsonObject()
                    ? apply(array.get(0).getAsJsonObject(), inherited, lossy)
                    : inherited;
            walk(array.get(0), inherited, out, lossy, emitted);
            for (int i = 1; i < array.size(); i++) {
                walk(array.get(i), base, out, lossy, emitted);
            }
            return;
        }

        JsonObject object = element.getAsJsonObject();
        Style style = apply(object, inherited, lossy);

        if (object.has("text")) {
            emit(object.get("text").getAsString(), style, out, emitted);
        } else if (object.has("translate")) {
            emit(object.get("translate").getAsString(), style, out, emitted);
        }

        JsonElement extra = object.get("extra");
        if (extra != null && extra.isJsonArray()) {
            for (JsonElement child : extra.getAsJsonArray()) {
                walk(child, style, out, lossy, emitted);
            }
        }
    }

    private static Style apply(JsonObject object, Style inherited, boolean[] lossy) {
        String color = inherited.color();
        if (object.has("color") && object.get("color").isJsonPrimitive()) {
            String value = object.get("color").getAsString().toLowerCase(Locale.ROOT);
            if (RichText.indexOfName(value) >= 0 || RichText.isHexColor(value)) {
                color = value;
            } else {
                lossy[0] = true; // unknown color
            }
        }
        for (String key : UNSUPPORTED_KEYS) {
            if (object.has(key)) {
                lossy[0] = true;
            }
        }
        return new Style(
                color,
                flag(object, "bold", inherited.bold()),
                flag(object, "italic", inherited.italic()),
                flag(object, "underlined", inherited.underlined()),
                flag(object, "strikethrough", inherited.strikethrough()),
                flag(object, "obfuscated", inherited.obfuscated()));
    }

    private static boolean flag(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static String codesOf(Style style) {
        StringBuilder codes = new StringBuilder();
        String color = style.color();
        if (color != null) {
            if (RichText.isHexColor(color)) {
                codes.append("&#").append(color.substring(1));
            } else {
                codes.append('&').append(RichText.codeOfName(color));
            }
        }
        if (style.bold()) codes.append("&l");
        if (style.italic()) codes.append("&o");
        if (style.underlined()) codes.append("&n");
        if (style.strikethrough()) codes.append("&m");
        if (style.obfuscated()) codes.append("&k");
        return codes.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&&").replace(SECTION, '&').replace("\n", "\\n");
    }

    private static void emit(String text, Style style, StringBuilder out, String[] emitted) {
        String codes = codesOf(style);
        if (!codes.equals(emitted[0])) {
            if (!emitted[0].isEmpty()) {
                out.append("&r"); // reset previous style
            }
            out.append(codes);
            emitted[0] = codes;
        }
        out.append(escape(text));
    }

    // ---------- editor text -> description ----------

    public static JsonElement toJson(String editable) {
        List<RichText.Run> runs = RichText.parse(editable);

        boolean hasHex = false;
        for (RichText.Run run : runs) {
            if (RichText.isHexColor(run.colorKey())) {
                hasHex = true;
                break;
            }
        }
        if (!hasHex) {
            return new JsonPrimitive(RichText.toLegacy(runs));
        }

        JsonArray extra = new JsonArray();
        for (RichText.Run run : runs) {
            JsonObject part = new JsonObject();
            part.addProperty("text", run.text());
            if (run.colorKey() != null) part.addProperty("color", run.colorKey());
            if ((run.formats() & RichText.BOLD) != 0) part.addProperty("bold", true);
            if ((run.formats() & RichText.ITALIC) != 0) part.addProperty("italic", true);
            if ((run.formats() & RichText.UNDERLINE) != 0) part.addProperty("underlined", true);
            if ((run.formats() & RichText.STRIKETHROUGH) != 0) part.addProperty("strikethrough", true);
            if ((run.formats() & RichText.OBFUSCATED) != 0) part.addProperty("obfuscated", true);
            extra.add(part);
        }

        JsonObject root = new JsonObject();
        root.addProperty("text", "");
        root.add("extra", extra);
        return root;
    }
}