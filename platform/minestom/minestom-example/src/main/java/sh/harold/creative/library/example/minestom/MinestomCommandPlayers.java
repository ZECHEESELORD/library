package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import sh.harold.creative.library.message.Message;

final class MinestomCommandPlayers {

    private MinestomCommandPlayers() {
    }

    static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Message.error("This command can only be used by a player.").send(sender);
        return null;
    }
}
