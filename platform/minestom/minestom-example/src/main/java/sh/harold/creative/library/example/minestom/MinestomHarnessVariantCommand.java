package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

final class MinestomHarnessVariantCommand extends Command {

    @FunctionalInterface
    interface VariantRunner {
        void run(Player player, String variant);
    }

    MinestomHarnessVariantCommand(
            String name,
            String alias,
            String defaultVariant,
            String usage,
            String[] variants,
            MinestomDevHarnessMessages feedback,
            VariantRunner runner
    ) {
        super(name, alias);

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            runner.run(player, defaultVariant);
        });

        var variant = ArgumentType.Word("variant").from(variants);
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            String value = context.get(variant);
            if ("help".equals(value)) {
                feedback.info(player, usage);
                return;
            }
            runner.run(player, value);
        }, variant);
    }
}
