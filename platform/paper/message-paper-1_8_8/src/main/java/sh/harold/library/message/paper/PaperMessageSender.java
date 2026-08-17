package sh.harold.library.message.paper;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.library.message.InlineMessage;
import sh.harold.library.message.MessageBlock;
import sh.harold.library.message.TitleMessage;

import java.util.Objects;

public final class PaperMessageSender implements AutoCloseable {
    private final BukkitAudiences audiences;

    public PaperMessageSender(Plugin plugin) {
        audiences = BukkitAudiences.create(Objects.requireNonNull(plugin, "plugin"));
    }

    public void send(CommandSender sender, InlineMessage message) {
        message.send(audiences.sender(sender));
    }

    public void send(CommandSender sender, MessageBlock block) {
        block.send(audiences.sender(sender));
    }

    public void sendActionBar(CommandSender sender, InlineMessage message) {
        message.sendActionBar(audiences.sender(sender));
    }

    public void showTitle(Player player, TitleMessage message) {
        message.show(audiences.player(player));
    }

    @Override
    public void close() {
        audiences.close();
    }
}
