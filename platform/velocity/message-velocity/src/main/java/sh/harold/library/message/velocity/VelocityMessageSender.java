package sh.harold.library.message.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import sh.harold.library.message.InlineMessage;
import sh.harold.library.message.MessageBlock;
import sh.harold.library.message.TitleMessage;

public final class VelocityMessageSender {

    public void send(CommandSource source, InlineMessage message) {
        message.send(asAudience(source));
    }

    public void send(CommandSource source, MessageBlock block) {
        block.send(asAudience(source));
    }

    public void sendActionBar(CommandSource source, InlineMessage message) {
        message.sendActionBar(asAudience(source));
    }

    public void showTitle(Player player, TitleMessage message) {
        message.show(asAudience(player));
    }

    private Audience asAudience(CommandSource source) {
        if (source instanceof Audience audience) {
            return audience;
        }
        throw new IllegalArgumentException("Velocity source does not implement Audience: " + source.getClass().getName());
    }
}
