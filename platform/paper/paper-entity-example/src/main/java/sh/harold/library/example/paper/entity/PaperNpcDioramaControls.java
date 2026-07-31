package sh.harold.library.example.paper.entity;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.entity.capability.Equipable;
import sh.harold.library.entity.house.HouseServiceClickContext;
import sh.harold.library.entity.house.HouseServiceEntity;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcAttentionLease;
import sh.harold.library.npc.behavior.NpcBehaviorSnapshot;
import sh.harold.library.npc.behavior.NpcPlayback;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Imperative and diagnostic controls kept separate from scene authoring. */
final class PaperNpcDioramaControls implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final Supplier<PaperNpcDioramaCast> castSupplier;
    private final Map<UUID, NpcAttentionLease> manualAttention = new ConcurrentHashMap<>();
    private final AtomicReference<NpcPlayback> lastPlayback = new AtomicReference<>();
    private final AtomicBoolean librarianUsesCompass = new AtomicBoolean();

    PaperNpcDioramaControls(
            JavaPlugin plugin,
            Supplier<PaperNpcDioramaCast> castSupplier
    ) {
        this.plugin = plugin;
        this.castSupplier = castSupplier;
    }

    void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                "npcdemo",
                "Control the NPC behavior dioramas near spawn.",
                List.of("testnpcs", "testentities"),
                new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack stack, String[] args) {
                        CommandSender sender = stack.getSender();
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage("This command can only be used by a player.");
                            return;
                        }
                        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
                            showHelp(player);
                            return;
                        }
                        PaperNpcDioramaCast cast = castSupplier.get();
                        if (cast == null) {
                            tell(player, Component.text("The NPC dioramas are still starting.", NamedTextColor.RED));
                            return;
                        }
                        run(player, cast, args[0].toLowerCase(Locale.ROOT));
                    }
                }
        ));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void greetLibrarian(HouseServiceClickContext context) {
        Player player = Bukkit.getPlayer(context.interactor().uniqueId());
        if (player == null) {
            return;
        }
        String delivery = context.action() + context.hand().map(hand -> " / " + hand).orElse("");
        tell(player, Component.text("Elowen: ", NamedTextColor.GOLD)
                .append(Component.text("Oh, hello. I was just finishing this entry. ", NamedTextColor.YELLOW))
                .append(Component.text("[normalized " + delivery + "]", NamedTextColor.DARK_GRAY)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        releaseManualAttention(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        releaseManualAttention(event.getPlayer().getUniqueId());
    }

    private void run(Player player, PaperNpcDioramaCast cast, String action) {
        HumanoidBehaviorCapable librarian = behavior(cast.librarian());
        try {
            switch (action) {
                case "say", "speak" -> {
                    librarian.speak(Component.text(
                            "I can queue that request after this catalogue note.",
                            NamedTextColor.GOLD
                    ));
                    remember(librarian.speak(Component.text(
                            "Then I will show you the map cabinet.",
                            NamedTextColor.AQUA
                    )));
                    tell(player, Component.text("Queued two shared librarian bubbles in FIFO order.", NamedTextColor.GREEN));
                }
                case "now" -> {
                    remember(librarian.speakNow(Component.text(
                            "Attention, readers: a loose page was found by the west shelves.",
                            NamedTextColor.RED
                    ).decorate(TextDecoration.BOLD)));
                    tell(player, Component.text("Superseded disposable speech with speakNow().", NamedTextColor.GREEN));
                }
                case "clear" -> {
                    librarian.clearSpeech();
                    tell(player, Component.text("Cleared visible and pending librarian speech.", NamedTextColor.GREEN));
                }
                case "cancel" -> cancelRemembered(player);
                case "study" -> {
                    remember(librarian.perform(cast.authored().librarianStudy()));
                    tell(player, Component.text(
                            "Elowen will begin an explicit lectern routine at the next cleanup checkpoint.",
                            NamedTextColor.GREEN
                    ));
                }
                case "forge" -> {
                    remember(behavior(cast.blacksmith()).perform(cast.authored().forgeWork()));
                    tell(player, Component.text(
                            "Bran will begin an explicit anvil routine at the next cleanup checkpoint.",
                            NamedTextColor.GREEN
                    ));
                }
                case "attention", "attend" -> holdAttention(player, librarian);
                case "snapshot" -> showSnapshot(player, librarian);
                case "off", "disable" -> librarian.disable().whenComplete((ignored, failure) -> tellCompletion(
                        player,
                        failure,
                        "Disabled Elowen's behavior; the native mannequin and authored base equipment remain."
                ));
                case "on", "configure" -> librarian.configure(cast.authored().librarianProfile())
                        .whenComplete((ignored, failure) -> tellCompletion(
                                player,
                                failure,
                                "Atomically reconfigured Elowen's immutable behavior profile."
                        ));
                case "baseprop" -> changeLibrarianBaseProp(player, cast);
                default -> showHelp(player);
            }
        } catch (RuntimeException failure) {
            tell(player, Component.text("Behavior command failed: " + failure.getMessage(), NamedTextColor.RED));
        }
    }

    private void cancelRemembered(Player player) {
        NpcPlayback playback = lastPlayback.getAndSet(null);
        if (playback == null || !playback.active()) {
            tell(player, Component.text("There is no active remembered playback.", NamedTextColor.GRAY));
            return;
        }
        playback.cancel();
        tell(player, Component.text("Cancelled the most recent speech or routine playback.", NamedTextColor.GREEN));
    }

    private void holdAttention(Player player, HumanoidBehaviorCapable librarian) {
        // Paper Player implements Identified, so this deliberately exercises the
        // convenience overload rather than reducing the player to a UUID here.
        NpcAttentionLease lease = librarian.attendTo(player);
        NpcAttentionLease previous = manualAttention.put(player.getUniqueId(), lease);
        if (previous != null) {
            previous.close();
        }
        var task = player.getScheduler().runDelayed(
                plugin,
                ignored -> releaseManualAttention(player.getUniqueId(), lease),
                () -> releaseManualAttention(player.getUniqueId(), lease),
                100L
        );
        if (task == null) {
            releaseManualAttention(player.getUniqueId(), lease);
            tell(player, Component.text("Could not retain attention because the viewer retired.", NamedTextColor.RED));
            return;
        }
        tell(player, Component.text(
                "Promoted you with attendTo(Identified) for five seconds; other sustained viewers keep personal overlays.",
                NamedTextColor.GREEN
        ));
    }

    private void changeLibrarianBaseProp(Player player, PaperNpcDioramaCast cast) {
        boolean compass = toggle(librarianUsesCompass);
        ItemDescriptor next = PaperNpcBehaviorCatalog.item(compass ? "compass" : "writable_book");
        plugin.getServer().getRegionScheduler().execute(plugin, cast.librarianLocation(), () -> {
            if (!cast.librarian().entity().spawned()) {
                return;
            }
            cast.librarian().entity().requireCapability(Equipable.class)
                    .equipment(EquipmentSlot.MAIN_HAND, next);
            plugin.getLogger().info(
                    "Changed Elowen's authored base prop to " + next.key()
                            + "; an active routine will restore this latest frame, not its start snapshot."
            );
            tell(player, Component.text(
                    "Changed Elowen's authored base prop to " + next.key().value()
                            + "; routine cleanup restores this latest value.",
                    NamedTextColor.GREEN
            ));
        });
    }

    private void showSnapshot(Player player, HumanoidBehaviorCapable behavior) {
        NpcBehaviorSnapshot snapshot = behavior.snapshot();
        String personality = behavior.profile()
                .map(profile -> profile.personality().name())
                .orElse("none");
        String routine = snapshot.activeRoutine().map(Key::asString).orElse("none");
        String target = snapshot.canonicalTarget().map(UUID::toString).orElse("none");
        tell(player, Component.text(
                "Elowen snapshot: configured=" + snapshot.configured()
                        + ", profile=" + personality
                        + ", activity=" + snapshot.activity()
                        + ", routine=" + routine
                        + ", target=" + target
                        + ", sessions=" + snapshot.acquisitionStack().size()
                        + ", queuedSpeech=" + snapshot.queuedSpeech()
                        + ", conversationLocked=" + snapshot.conversationLocked()
                        + ", revision=" + snapshot.revision(),
                NamedTextColor.AQUA
        ));
    }

    private void showHelp(Player player) {
        tell(player, Component.text("NPC dioramas: ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(
                        "/npcdemo say|now|clear|cancel|study|forge|attention|snapshot|baseprop|off|on",
                        NamedTextColor.YELLOW
                )));
    }

    private void tellCompletion(Player player, Throwable failure, String success) {
        if (failure == null) {
            tell(player, Component.text(success, NamedTextColor.GREEN));
        } else {
            tell(player, Component.text("Behavior operation failed: " + failure.getMessage(), NamedTextColor.RED));
        }
    }

    private void tell(Player player, Component message) {
        player.getScheduler().execute(plugin, () -> player.sendMessage(message), null, 1L);
    }

    private void remember(NpcPlayback playback) {
        lastPlayback.set(playback);
    }

    private void releaseManualAttention(UUID viewerId) {
        NpcAttentionLease lease = manualAttention.remove(viewerId);
        if (lease != null) {
            lease.close();
        }
    }

    private void releaseManualAttention(UUID viewerId, NpcAttentionLease expected) {
        if (manualAttention.remove(viewerId, expected)) {
            expected.close();
        }
    }

    private static boolean toggle(AtomicBoolean value) {
        boolean current;
        boolean next;
        do {
            current = value.get();
            next = !current;
        } while (!value.compareAndSet(current, next));
        return next;
    }

    private static HumanoidBehaviorCapable behavior(HouseServiceEntity service) {
        return service.entity().requireCapability(HumanoidBehaviorCapable.class);
    }

    @Override
    public void close() {
        manualAttention.values().forEach(NpcAttentionLease::close);
        manualAttention.clear();
        NpcPlayback playback = lastPlayback.getAndSet(null);
        if (playback != null) {
            playback.cancel();
        }
    }
}
