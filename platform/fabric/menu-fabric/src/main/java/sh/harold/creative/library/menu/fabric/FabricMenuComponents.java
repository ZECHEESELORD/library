package sh.harold.creative.library.menu.fabric;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ComponentSerialization;

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

    static Component toAdventurePlain(net.minecraft.network.chat.Component component) {
        return Component.text(Objects.requireNonNull(component, "component").getString());
    }
}
