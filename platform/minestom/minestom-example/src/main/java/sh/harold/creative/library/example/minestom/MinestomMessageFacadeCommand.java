package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class MinestomMessageFacadeCommand extends Command {

    public MinestomMessageFacadeCommand(MinestomMessageFacadeExamples examples, MinestomDevHarnessMessages feedback) {
        super("testmessages", "testmessagefacade");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            examples.sendAll(player);
        });

        var variant = ArgumentType.Word("variant").from("all", "notices", "topics", "clicks", "block", "help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "all" -> examples.sendAll(player);
                case "notices" -> examples.sendNotices(player);
                case "topics" -> examples.sendTopics(player);
                case "clicks" -> examples.sendClickPrompts(player);
                case "block" -> examples.sendBlocks(player);
                case "help" -> feedback.sendSummary(player);
                default -> throw new IllegalStateException("Unhandled message variant");
            }
        }, variant);
    }
}
