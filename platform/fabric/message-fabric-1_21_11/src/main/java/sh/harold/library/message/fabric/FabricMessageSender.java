package sh.harold.library.message.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import sh.harold.library.message.InlineMessage;
import sh.harold.library.message.MessageBlock;
import sh.harold.library.message.TitleMessage;

import java.time.Duration;
import java.util.Objects;

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

    public void showTitle(ServerPlayer player, TitleMessage message) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        var registries = player.level().registryAccess();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                toTitleTicks(message.fadeIn()),
                toTitleTicks(message.stay()),
                toTitleTicks(message.fadeOut())
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                FabricMessageComponents.toNative(message.subtitle(), registries)
        ));
        player.connection.send(new ClientboundSetTitleTextPacket(
                FabricMessageComponents.toNative(message.title(), registries)
        ));
    }

    static int toTitleTicks(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
        long ticks = Math.multiplyExact(duration.getSeconds(), 20L);
        ticks = Math.addExact(ticks, duration.getNano() / 50_000_000L);
        if (duration.getNano() % 50_000_000 != 0) {
            ticks = Math.addExact(ticks, 1L);
        }
        return Math.toIntExact(ticks);
    }
}
