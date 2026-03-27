package sh.harold.creative.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import sh.harold.creative.library.menu.minestom.MinestomMenuPlatform;

public final class MinestomMenuExamplesCommand extends Command {

    public MinestomMenuExamplesCommand(
            MinestomMenuPlatform menus,
            MinestomMenuExampleMenus examples,
            MinestomDevHarnessMessages feedback
    ) {
        super("testmenus", "testmenu");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            menus.open(player, examples.tabsGallery());
        });

        var variant = ArgumentType.Word("variant")
                .from("tabs", "list", "profile", "farming", "museum", "slot5", "canvas", "help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "tabs" -> menus.open(player, examples.tabsGallery());
                case "list" -> menus.open(player, examples.listGallery());
                case "profile" -> menus.open(player, examples.profilePreview());
                case "farming" -> menus.open(player, examples.farmingPreview());
                case "museum" -> menus.open(player, examples.museumPreview());
                case "slot5" -> menus.open(player, examples.slotFivePreview());
                case "canvas" -> menus.open(player, examples.canvasGallery());
                case "help" -> feedback.sendSummary(player);
                default -> throw new IllegalStateException("Unhandled menu variant");
            }
        }, variant);
    }
}
