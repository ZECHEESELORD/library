package sh.harold.library.message.paper;

import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.library.message.InlineMessage;
import sh.harold.library.message.MessageBlock;
import sh.harold.library.message.TitleMessage;

public final class PaperMessageSender {

    public void send(CommandSender sender, InlineMessage message) {
        message.send(asAudience(sender));
    }

    public void send(CommandSender sender, MessageBlock block) {
        block.send(asAudience(sender));
    }

    public void sendActionBar(CommandSender sender, InlineMessage message) {
        message.sendActionBar(asAudience(sender));
    }

    public void showTitle(Player player, TitleMessage message) {
        message.show(asAudience(player));
    }

    private Audience asAudience(CommandSender sender) {
        if (sender instanceof Audience audience) {
            return audience;
        }
        throw new IllegalArgumentException("Paper sender does not implement Audience: " + sender.getClass().getName());
    }
}
