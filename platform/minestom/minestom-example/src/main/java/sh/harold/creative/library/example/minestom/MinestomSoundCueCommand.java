package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class MinestomSoundCueCommand extends Command {

    public MinestomSoundCueCommand(MinestomSoundCueExamples examples, MinestomDevHarnessMessages feedback) {
        super("testsoundfx", "testsounds");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            examples.playAll(player);
        });

        var variant = ArgumentType.Word("variant")
                .from("all", "menu", "npc", "confirm", "deny", "levelup", "discovery", "help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "all" -> examples.playAll(player);
                case "menu", "npc", "confirm", "deny", "levelup", "discovery" ->
                        examples.playVariant(player, context.get(variant));
                case "help" -> feedback.sendSummary(player);
                default -> throw new IllegalStateException("Unhandled sound variant");
            }
        }, variant);
    }
}
