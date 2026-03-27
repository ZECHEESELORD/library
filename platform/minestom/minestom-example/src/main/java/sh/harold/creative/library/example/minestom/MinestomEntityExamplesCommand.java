package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class MinestomEntityExamplesCommand extends Command {

    public MinestomEntityExamplesCommand(MinestomEntityExampleHarness examples, MinestomDevHarnessMessages feedback) {
        super("testnpcs", "testentities");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            examples.reset();
            feedback.success(player, "Reset the entity and House preview near spawn.");
        });

        var variant = ArgumentType.Word("variant").from("reset", "clear", "help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "reset" -> {
                    examples.reset();
                    feedback.success(player, "Reset the entity and House preview near spawn.");
                }
                case "clear" -> {
                    examples.clear();
                    feedback.success(player, "Cleared the entity and House preview.");
                }
                case "help" -> feedback.sendSummary(player);
                default -> throw new IllegalStateException("Unhandled entity variant");
            }
        }, variant);
    }
}
