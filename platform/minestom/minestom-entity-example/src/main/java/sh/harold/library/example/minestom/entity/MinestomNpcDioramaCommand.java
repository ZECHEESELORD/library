package sh.harold.library.example.minestom.entity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import sh.harold.library.npc.behavior.NpcBehaviorSnapshot;
import sh.harold.library.npc.behavior.NpcPlayback;

import java.util.concurrent.CompletionStage;

/** Player-driven controls for imperative behavior operations that are not ambient authoring. */
final class MinestomNpcDioramaCommand extends Command {

    MinestomNpcDioramaCommand(MinestomNpcDioramas dioramas) {
        super("npcdemo", "npcdiorama");
        setDefaultExecutor((sender, context) -> help(sender));

        var action = ArgumentType.Word("action").from(
                "help",
                "library",
                "forge",
                "watchpost",
                "queue",
                "now",
                "clear",
                "lectern",
                "review",
                "catalogue",
                "shelf",
                "anvil",
                "craft",
                "inspect",
                "guard",
                "sleepy",
                "route",
                "attend",
                "snapshot",
                "pause",
                "resume"
        );
        addSyntax((sender, context) -> {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            String selected = context.get(action);
            if (selected.equals("help")) {
                help(player);
                return;
            }
            if (!dioramas.ready()) {
                player.sendMessage(Component.text(
                        "The NPC actors are still configuring. Try again in a moment.",
                        NamedTextColor.RED
                ));
                return;
            }
            try {
                switch (selected) {
                    case "library" -> teleport(player, MinestomNpcDioramas.LIBRARY_VIEW, "library");
                    case "forge" -> teleport(player, MinestomNpcDioramas.FORGE_VIEW, "forge");
                    case "watchpost" -> teleport(player, MinestomNpcDioramas.WATCH_VIEW, "watch post");
                    case "queue" -> {
                        dioramas.queueLibrarySpeech();
                        success(player, "Queued three global speech bubbles on the librarian FIFO.");
                    }
                    case "now" -> {
                        dioramas.speakNow();
                        success(player, "Used speakNow: pending disposable speech was superseded.");
                    }
                    case "clear" -> {
                        dioramas.clearSpeech();
                        success(player, "Cleared visible and pending librarian speech.");
                    }
                    case "lectern" -> perform(player, dioramas, NpcDioramaCatalog.LECTERN);
                    case "review" -> perform(player, dioramas, NpcDioramaCatalog.LECTERN_REVIEW);
                    case "catalogue" -> perform(player, dioramas, NpcDioramaCatalog.CATALOGUING);
                    case "shelf" -> perform(player, dioramas, NpcDioramaCatalog.SHELF);
                    case "anvil" -> perform(player, dioramas, NpcDioramaCatalog.ANVIL);
                    case "craft" -> perform(player, dioramas, NpcDioramaCatalog.CRAFTING);
                    case "inspect" -> perform(player, dioramas, NpcDioramaCatalog.SMITH_INSPECTION);
                    case "guard" -> perform(player, dioramas, NpcDioramaCatalog.GUARD_SCAN);
                    case "sleepy" -> perform(player, dioramas, NpcDioramaCatalog.SLEEPY_WATCH);
                    case "route" -> perform(player, dioramas, NpcDioramaCatalog.ROUTE_CHECK);
                    case "attend" -> {
                        dioramas.attendTo(player);
                        success(player, "The librarian now holds a five-second manual attention lease on you.");
                    }
                    case "snapshot" -> snapshot(
                            player,
                            dioramas.librarianProfileName(),
                            dioramas.librarianSnapshot()
                    );
                    case "pause" -> lifecycle(player, dioramas.pauseLibrarian(),
                            "Disabled the librarian profile; the native mannequin is inert.");
                    case "resume" -> lifecycle(player, dioramas.resumeLibrarian(),
                            "Reconfigured the immutable librarian profile from a clean state.");
                    default -> throw new IllegalStateException("Unhandled NPC demo action: " + selected);
                }
            } catch (RuntimeException failure) {
                player.sendMessage(Component.text("NPC demo command failed: " + failure.getMessage(), NamedTextColor.RED));
            }
        }, action);
    }

    private static void perform(Player player, MinestomNpcDioramas dioramas, String routineName) {
        NpcPlayback playback = dioramas.perform(routineName);
        success(player, "Explicitly preempted ambient work with routine " + routineName + ".");
        playback.completion().whenComplete((ignored, failure) -> {
            if (player.isOnline()) {
                player.sendMessage(Component.text(
                        failure == null
                                ? "Routine " + routineName + " reached its cleanup checkpoint; base props were restored."
                                : "Routine " + routineName + " ended exceptionally: " + failure.getMessage(),
                        failure == null ? NamedTextColor.GRAY : NamedTextColor.RED
                ));
            }
        });
    }

    private static void lifecycle(Player player, CompletionStage<Void> stage, String successMessage) {
        stage.whenComplete((ignored, failure) -> {
            if (!player.isOnline()) {
                return;
            }
            if (failure == null) {
                success(player, successMessage);
            } else {
                player.sendMessage(Component.text("Lifecycle operation failed: " + failure.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private static void snapshot(Player player, String profile, NpcBehaviorSnapshot snapshot) {
        player.sendMessage(Component.text("Librarian snapshot", NamedTextColor.GOLD)
                .append(Component.text(
                        ": profile=" + profile
                                + ", configured=" + snapshot.configured()
                                + ", activity=" + snapshot.activity()
                                + ", target=" + snapshot.canonicalTarget().map(Object::toString).orElse("none")
                                + ", sessions=" + snapshot.acquisitionStack().size()
                                + ", routine=" + snapshot.activeRoutine().map(Object::toString).orElse("none")
                                + ", queuedSpeech=" + snapshot.queuedSpeech()
                                + ", conversationLocked=" + snapshot.conversationLocked()
                                + ", revision=" + snapshot.revision(),
                        NamedTextColor.GRAY
                )));
    }

    private static void teleport(Player player, net.minestom.server.coordinate.Pos destination, String name) {
        player.teleport(destination);
        success(player, "Moved to the " + name + " viewing point. NPCs remain at their authored anchors.");
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(Component.text("NPC behavior showcase", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                "/npcdemo library|forge|watchpost - visit a diorama (all eight personalities are represented)",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                "/npcdemo queue|now|clear - speak, speakNow, and clearSpeech", NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                "/npcdemo lectern|review|catalogue|shelf|anvil|craft|inspect|guard|sleepy|route - perform a routine",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                "/npcdemo attend|snapshot|pause|resume - attention lease, snapshot, disable, and configure",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                "Walk into attention range with multiple players, and interact with any actor to see per-viewer gaze/barks plus normalized chat callbacks.",
                NamedTextColor.AQUA
        ));
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text("This showcase command requires a player.", NamedTextColor.RED));
        return null;
    }

    private static void success(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }
}
