package sh.harold.creative.library.message.fabric.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ComponentSerialization;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.fabric.FabricMessageComponents;

import java.util.Objects;

public final class FabricClientMessageSender {

    public void sendToClient(InlineMessage message) {
        requirePlayer().sendSystemMessage(toNative(FabricMessageComponents.renderChat(message), requireRegistries()));
    }

    public void sendToClient(MessageBlock block) {
        requirePlayer().sendSystemMessage(toNative(FabricMessageComponents.renderChat(block), requireRegistries()));
    }

    public void send(LocalPlayer player, InlineMessage message) {
        Objects.requireNonNull(player, "player").sendSystemMessage(toNative(FabricMessageComponents.renderChat(message), requireRegistries()));
    }

    public void send(LocalPlayer player, MessageBlock block) {
        Objects.requireNonNull(player, "player").sendSystemMessage(toNative(FabricMessageComponents.renderChat(block), requireRegistries()));
    }

    public void sendActionBarToClient(InlineMessage message) {
        requirePlayer().sendOverlayMessage(toNative(FabricMessageComponents.renderActionBar(message), requireRegistries()));
    }

    public void sendActionBar(LocalPlayer player, InlineMessage message) {
        Objects.requireNonNull(player, "player").sendOverlayMessage(toNative(FabricMessageComponents.renderActionBar(message), requireRegistries()));
    }

    private static LocalPlayer requirePlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("Fabric client player is not available");
        }
        return player;
    }

    private static HolderLookup.Provider requireRegistries() {
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("Fabric client registries are not available");
        }
        return Minecraft.getInstance().level.registryAccess();
    }

    private static net.minecraft.network.chat.Component toNative(net.kyori.adventure.text.Component component, HolderLookup.Provider registries) {
        String json = GsonComponentSerializer.gson().serialize(Objects.requireNonNull(component, "component"));
        return ComponentSerialization.CODEC.parse(
                Objects.requireNonNull(registries, "registries").createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(json)
        ).result().orElseThrow(() -> new IllegalStateException("Failed to convert Adventure component to native chat component"));
    }
}
