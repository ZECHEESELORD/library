package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import sh.harold.creative.library.message.Message;

public final class MinestomCooldownCommand extends Command {

    public MinestomCooldownCommand(MinestomCooldownHarness harness, MinestomDevHarnessMessages feedback) {
        super("testcooldowns");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            harness.runKeys(player);
        });

        var variant = ArgumentType.Word("variant").from("keys", "local", "shared", "context", "menu", "npc", "clear", "help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "keys" -> harness.runKeys(player);
                case "local" -> harness.runLocal(player);
                case "shared" -> harness.runShared(player);
                case "context" -> harness.runContext(player);
                case "menu" -> harness.openMenu(player);
                case "npc" -> harness.resetNpc(player);
                case "clear" -> harness.clear(player);
                case "help" -> feedback.info(
                        player,
                        "Use {command} keys|local|shared|context|menu|npc|clear|help.",
                        Message.slot("command", feedback.command("/testcooldowns"))
                );
                default -> throw new IllegalStateException("Unhandled cooldown variant");
            }
        }, variant);
    }
}
