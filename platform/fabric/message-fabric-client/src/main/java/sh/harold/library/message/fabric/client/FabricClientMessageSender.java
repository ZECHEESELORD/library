package sh.harold.library.message.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import sh.harold.library.message.InlineMessage;
import sh.harold.library.message.MessageBlock;
import sh.harold.library.message.TitleMessage;
import sh.harold.library.message.fabric.FabricMessageComponents;

import java.time.Duration;
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

    public void showTitleToClient(TitleMessage message) {
        showTitle(Objects.requireNonNull(message, "message"));
    }

    public void showTitle(LocalPlayer player, TitleMessage message) {
        Objects.requireNonNull(player, "player");
        showTitle(Objects.requireNonNull(message, "message"));
    }

    private static void showTitle(TitleMessage message) {
        var hud = Minecraft.getInstance().gui.hud;
        var registries = requireRegistries();
        hud.setTimes(toTitleTicks(message.fadeIn()), toTitleTicks(message.stay()), toTitleTicks(message.fadeOut()));
        hud.setSubtitle(FabricMessageComponents.toNative(message.subtitle(), registries));
        hud.setTitle(FabricMessageComponents.toNative(message.title(), registries));
    }

    private static int toTitleTicks(Duration duration) {
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
