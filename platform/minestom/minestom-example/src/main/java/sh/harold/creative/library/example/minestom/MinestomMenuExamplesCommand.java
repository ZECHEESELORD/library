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
                .from("tabs", "list", "reactive", "snake", "lockdrag", "lockclick", "profile", "farming", "museum", "slot5", "canvas", "help");
        var traceAction = ArgumentType.Word("traceAction").from("trace");
        var traceMode = ArgumentType.Word("traceMode").from("off", "all", "lockdrag", "lockclick", "status");
        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(variant)) {
                case "tabs" -> menus.open(player, examples.tabsGallery());
                case "list" -> menus.open(player, examples.listGallery());
                case "reactive" -> menus.open(player, examples.reactiveGallery());
                case "snake" -> menus.open(player, examples.snakeDemo());
                case "lockdrag" -> menus.open(player, examples.lockDragDemo());
                case "lockclick" -> menus.open(player, examples.lockClickDemo());
                case "profile" -> menus.open(player, examples.profilePreview());
                case "farming" -> menus.open(player, examples.farmingPreview());
                case "museum" -> menus.open(player, examples.museumPreview());
                case "slot5" -> menus.open(player, examples.slotFivePreview());
                case "canvas" -> menus.open(player, examples.canvasGallery());
                case "help" -> feedback.sendSummary(player);
                default -> throw new IllegalStateException("Unhandled menu variant");
            }
        }, variant);

        addSyntax((sender, context) -> {
            Player player = MinestomCommandPlayers.requirePlayer(sender);
            if (player == null) {
                return;
            }

            switch (context.get(traceMode)) {
                case "off" -> {
                    menus.trace().disable();
                    feedback.sendMenuTraceUpdated(player, "off");
                }
                case "all" -> {
                    menus.trace().traceAll();
                    feedback.sendMenuTraceUpdated(player, "all");
                }
                case "lockdrag" -> {
                    menus.trace().traceMenuTitles(java.util.List.of(MinestomMenuExampleMenus.LOCK_DRAG_TITLE));
                    feedback.sendMenuTraceUpdated(player, "lockdrag");
                }
                case "lockclick" -> {
                    menus.trace().traceMenuTitles(java.util.List.of(MinestomMenuExampleMenus.LOCK_CLICK_TITLE));
                    feedback.sendMenuTraceUpdated(player, "lockclick");
                }
                case "status" -> feedback.sendMenuTraceStatus(player, menus.trace().snapshot());
                default -> throw new IllegalStateException("Unhandled trace mode");
            }
        }, traceAction, traceMode);
    }
}
