package sh.harold.creative.library.message.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;

public final class FabricMessageSender {

    public void send(CommandSourceStack source, InlineMessage message) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(message), player.level().registryAccess()), false);
            return;
        }
        source.sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(message), source.getServer().registryAccess()));
    }

    public void send(ServerPlayer player, InlineMessage message) {
        player.sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(message), player.level().registryAccess()), false);
    }

    public void send(CommandSourceStack source, MessageBlock block) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(block), player.level().registryAccess()), false);
            return;
        }
        source.sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(block), source.getServer().registryAccess()));
    }

    public void send(ServerPlayer player, MessageBlock block) {
        player.sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderChat(block), player.level().registryAccess()), false);
    }

    public void sendActionBar(CommandSourceStack source, InlineMessage message) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.displayClientMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderActionBar(message), player.level().registryAccess()), true);
            return;
        }
        source.sendSystemMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderActionBar(message), source.getServer().registryAccess()));
    }

    public void sendActionBar(ServerPlayer player, InlineMessage message) {
        player.displayClientMessage(FabricMessageComponents.toNative(FabricMessageComponents.renderActionBar(message), player.level().registryAccess()), true);
    }
}
