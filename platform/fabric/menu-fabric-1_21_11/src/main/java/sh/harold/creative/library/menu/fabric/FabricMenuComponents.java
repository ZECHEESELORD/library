package sh.harold.creative.library.menu.fabric;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;

import java.util.Objects;

final class FabricMenuComponents {

    private FabricMenuComponents() {
    }

    static net.minecraft.network.chat.Component toNative(Component component, HolderLookup.Provider registries) {
        String json = GsonComponentSerializer.gson().serialize(Objects.requireNonNull(component, "component"));
        return ComponentSerialization.CODEC.parse(
                Objects.requireNonNull(registries, "registries").createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(json)
        ).result().orElseThrow(() -> new IllegalStateException("Failed to convert Adventure component to native component"));
    }

    static Component toAdventure(net.minecraft.network.chat.Component component) {
        Objects.requireNonNull(component, "component");
        Component converted = applyStyle(Component.text(component.plainCopy().getString()), component.getStyle());
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) {
            converted = converted.append(toAdventure(sibling));
        }
        return converted;
    }

    static Component toAdventurePlain(net.minecraft.network.chat.Component component) {
        return Component.text(Objects.requireNonNull(component, "component").getString());
    }

    private static Component applyStyle(Component component, Style style) {
        if (style == null || style.isEmpty()) {
            return component;
        }
        if (style.getColor() != null) {
            component = component.color(TextColor.color(style.getColor().getValue()));
        }
        component = component.decoration(TextDecoration.BOLD, style.isBold());
        component = component.decoration(TextDecoration.ITALIC, style.isItalic());
        component = component.decoration(TextDecoration.UNDERLINED, style.isUnderlined());
        component = component.decoration(TextDecoration.STRIKETHROUGH, style.isStrikethrough());
        component = component.decoration(TextDecoration.OBFUSCATED, style.isObfuscated());
        return component;
    }
}
