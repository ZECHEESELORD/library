package sh.harold.creative.library.message.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.fabric.FabricMessageComponents;

import java.util.Objects;

public final class FabricClientMessageSender {

    public void sendToClient(InlineMessage message) {
        requirePlayer().sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(message), requireRegistries()));
    }

    public void sendToClient(MessageBlock block) {
        requirePlayer().sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(block), requireRegistries()));
    }

    public void send(LocalPlayer player, InlineMessage message) {
        Objects.requireNonNull(player, "player").sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(message), requireRegistries()));
    }

    public void send(LocalPlayer player, MessageBlock block) {
        Objects.requireNonNull(player, "player").sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(block), requireRegistries()));
    }

    public void sendActionBarToClient(InlineMessage message) {
        requirePlayer().sendOverlayMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderActionBar(message), requireRegistries()));
    }

    public void sendActionBar(LocalPlayer player, InlineMessage message) {
        Objects.requireNonNull(player, "player").sendOverlayMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderActionBar(message), requireRegistries()));
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
}
