package sh.harold.creative.library.message.fabric;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;

import java.util.Objects;

public final class FabricMessageSender {

    public void send(CommandSourceStack source, InlineMessage message) {
        source.sendSystemMessage(toNative(FabricMessageComponents.renderChat(message), source.getServer().registryAccess()));
    }

    public void send(ServerPlayer player, InlineMessage message) {
        player.sendSystemMessage(toNative(FabricMessageComponents.renderChat(message), player.level().registryAccess()));
    }

    public void send(CommandSourceStack source, MessageBlock block) {
        source.sendSystemMessage(toNative(FabricMessageComponents.renderChat(block), source.getServer().registryAccess()));
    }

    public void send(ServerPlayer player, MessageBlock block) {
        player.sendSystemMessage(toNative(FabricMessageComponents.renderChat(block), player.level().registryAccess()));
    }

    public void sendActionBar(CommandSourceStack source, InlineMessage message) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(toNative(FabricMessageComponents.renderActionBar(message), player.level().registryAccess()));
            return;
        }
        source.sendSystemMessage(toNative(FabricMessageComponents.renderActionBar(message), source.getServer().registryAccess()));
    }

    public void sendActionBar(ServerPlayer player, InlineMessage message) {
        player.sendOverlayMessage(toNative(FabricMessageComponents.renderActionBar(message), player.level().registryAccess()));
    }

    private static net.minecraft.network.chat.Component toNative(net.kyori.adventure.text.Component component, HolderLookup.Provider registries) {
        String json = GsonComponentSerializer.gson().serialize(Objects.requireNonNull(component, "component"));
        return ComponentSerialization.CODEC.parse(
                Objects.requireNonNull(registries, "registries").createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(json)
        ).result().orElseThrow(() -> new IllegalStateException("Failed to convert Adventure component to native chat component"));
    }
}
