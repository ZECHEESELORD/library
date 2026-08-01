package sh.harold.library.example.minestom;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import sh.harold.library.menu.minestom.MinestomMenuPlatform;

public final class MinestomMessageFacadeCommand extends Command {

    public MinestomMessageFacadeCommand(
            MinestomMenuPlatform menus,
            MinestomMessageShowcase showcase,
            MinestomDevHarnessMessages feedback
    ) {
        super("testmessages", "testmessagefacade");

        setDefaultExecutor((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            menus.open(player, showcase.panel(player));
        });

        var help = ArgumentType.Literal("help");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            feedback.sendSummary(player);
        }, help);

        var chatMenu = ArgumentType.Literal("chatmenu");
        var page = ArgumentType.Integer("page").min(1);
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }
            showcase.sendChatMenuPage(player, context.get(page));
        }, chatMenu, page);
    }
}
