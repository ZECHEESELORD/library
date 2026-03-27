package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

final class MinestomCommandPlayers {

    private MinestomCommandPlayers() {
    }

    static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("This command can only be used by a player.");
        return null;
    }
}
