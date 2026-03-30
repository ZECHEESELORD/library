package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class MinestomCameraMotionCommand extends Command {

    public MinestomCameraMotionCommand(MinestomCameraMotionExamples examples, MinestomDevHarnessMessages feedback) {
        super("testcamera", "testcameramotion");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            examples.playAll(player);
        });

        var variant = ArgumentType.Word("variant")
                .from("all", "recoil", "rumble", "concussion", "stagger", "cinematic", "stop", "help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "all" -> examples.playAll(player);
                case "recoil", "rumble", "concussion", "stagger", "cinematic" ->
                        examples.playVariant(player, context.get(variant));
                case "stop" -> examples.stop(player);
                case "help" -> feedback.sendSummary(player);
                default -> throw new IllegalStateException("Unhandled camera motion variant");
            }
        }, variant);
    }
}
