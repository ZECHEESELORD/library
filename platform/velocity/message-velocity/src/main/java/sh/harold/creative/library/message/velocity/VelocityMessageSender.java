package sh.harold.creative.library.message.velocity;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.audience.Audience;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;

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

    private Audience asAudience(CommandSource source) {
        if (source instanceof Audience audience) {
            return audience;
        }
        throw new IllegalArgumentException("Velocity source does not implement Audience: " + source.getClass().getName());
    }
}
