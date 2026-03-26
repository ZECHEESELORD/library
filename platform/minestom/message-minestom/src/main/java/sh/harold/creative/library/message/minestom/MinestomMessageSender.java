package sh.harold.creative.library.message.minestom;

import net.kyori.adventure.audience.Audience;
import net.minestom.server.entity.Player;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;

public final class MinestomMessageSender {

    public void send(Player player, InlineMessage message) {
        message.send(asAudience(player));
    }

    public void send(Player player, MessageBlock block) {
        block.send(asAudience(player));
    }

    public void sendActionBar(Player player, InlineMessage message) {
        message.sendActionBar(asAudience(player));
    }

    private Audience asAudience(Player player) {
        if (player instanceof Audience audience) {
            return audience;
        }
        throw new IllegalArgumentException("Minestom player does not implement Audience: " + player.getClass().getName());
    }
}
