package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class MinestomScreenOverlayCommand extends Command {

    public MinestomScreenOverlayCommand(MinestomScreenOverlayExamples examples, MinestomDevHarnessMessages feedback) {
        super("testoverlays", "testoverlay");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            examples.runDemo(player);
        });

        var variant = ArgumentType.Word("variant").from("demo", "clear", "help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "demo" -> examples.runDemo(player);
                case "clear" -> {
                    examples.clear(player);
                    feedback.success(player, "Cleared active screen overlays.");
                }
                case "help" -> feedback.sendSummary(player);
                default -> throw new IllegalStateException("Unhandled overlay variant");
            }
        }, variant);
    }
}
