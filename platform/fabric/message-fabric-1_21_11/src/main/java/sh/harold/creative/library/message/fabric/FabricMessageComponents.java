package sh.harold.creative.library.message.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ComponentSerialization;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;

import java.util.Objects;

public final class FabricMessageComponents {

    private FabricMessageComponents() {
    }

    public static Component renderChat(InlineMessage message) {
        CaptureAudience capture = new CaptureAudience();
        Objects.requireNonNull(message, "message").send(capture);
        return capture.requireChat();
    }

    public static Component renderActionBar(InlineMessage message) {
        CaptureAudience capture = new CaptureAudience();
        Objects.requireNonNull(message, "message").sendActionBar(capture);
        return capture.requireActionBar();
    }

    public static Component renderChat(MessageBlock block) {
        CaptureAudience capture = new CaptureAudience();
        Objects.requireNonNull(block, "block").send(capture);
        return capture.requireChat();
    }

    public static net.minecraft.network.chat.Component toNative(Component component, HolderLookup.Provider registries) {
        return ComponentSerialization.CODEC.parse(
                Objects.requireNonNull(registries, "registries").createSerializationContext(JsonOps.INSTANCE),
                normalizedSerializedComponent(component)
        ).result().orElseThrow(() -> new IllegalStateException("Failed to convert Adventure component to native chat component"));
    }

    static net.minecraft.network.chat.Component toNative(Component component) {
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, normalizedSerializedComponent(component))
                .result()
                .orElseThrow(() -> new IllegalStateException("Failed to convert Adventure component to native chat component"));
    }

    private static JsonElement normalizedSerializedComponent(Component component) {
        String json = GsonComponentSerializer.gson().serialize(Objects.requireNonNull(component, "component"));
        return normalizeComponent(JsonParser.parseString(json));
    }

    private static JsonElement normalizeComponent(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray source = element.getAsJsonArray();
            JsonArray normalized = new JsonArray(source.size());
            for (JsonElement child : source) {
                normalized.add(normalizeComponent(child));
            }
            return normalized;
        }
        JsonObject source = element.getAsJsonObject();
        JsonObject normalized = new JsonObject();
        for (var entry : source.entrySet()) {
            normalized.add(entry.getKey(), normalizeComponent(entry.getValue()));
        }
        JsonElement clickEvent = firstObject(normalized, "click_event", "clickEvent");
        if (clickEvent != null) {
            JsonElement normalizedClickEvent = normalizeClickEvent(clickEvent);
            normalized.add("click_event", normalizedClickEvent);
            normalized.add("clickEvent", normalizedClickEvent);
        }
        return normalized;
    }

    private static JsonElement normalizeClickEvent(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return element;
        }
        JsonObject normalized = element.getAsJsonObject().deepCopy();
        String action = stringValue(normalized.get("action"));
        JsonElement value = normalized.get("value");
        if (value == null || action == null) {
            return normalized;
        }
        switch (action) {
            case "open_url" -> copyIfAbsent(normalized, "url", value);
            case "run_command", "suggest_command" -> copyIfAbsent(normalized, "command", value);
            case "change_page" -> copyPageIfAbsent(normalized, value);
            default -> {
            }
        }
        return normalized;
    }

    private static JsonElement firstObject(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonObject()) {
                return value;
            }
        }
        return null;
    }

    private static void copyIfAbsent(JsonObject object, String key, JsonElement value) {
        if (!object.has(key)) {
            object.add(key, value.deepCopy());
        }
    }

    private static void copyPageIfAbsent(JsonObject object, JsonElement value) {
        if (object.has("page")) {
            return;
        }
        try {
            object.add("page", new JsonPrimitive(value.getAsInt()));
        } catch (NumberFormatException | IllegalStateException ignored) {
            object.add("page", value.deepCopy());
        }
    }

    private static String stringValue(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static final class CaptureAudience implements Audience {

        private Component chat;
        private Component actionBar;

        @Override
        public void sendMessage(Component message) {
            this.chat = Objects.requireNonNull(message, "message");
        }

        @Override
        public void sendActionBar(Component message) {
            this.actionBar = Objects.requireNonNull(message, "message");
        }

        private Component requireChat() {
            if (chat == null) {
                throw new IllegalStateException("Message did not render chat output");
            }
            return chat;
        }

        private Component requireActionBar() {
            if (actionBar == null) {
                throw new IllegalStateException("Message did not render action-bar output");
            }
            return actionBar;
        }
    }
}
