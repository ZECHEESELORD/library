package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.Material;
import sh.harold.creative.library.cooldown.CooldownAcquisition;
import sh.harold.creative.library.cooldown.CooldownKey;
import sh.harold.creative.library.cooldown.CooldownKeys;
import sh.harold.creative.library.cooldown.CooldownRef;
import sh.harold.creative.library.cooldown.CooldownRegistry;
import sh.harold.creative.library.cooldown.CooldownSpec;
import sh.harold.creative.library.cooldown.core.InMemoryCooldownRegistry;
import sh.harold.creative.library.entity.CommonEntityFlags;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.InteractorRef;
import sh.harold.creative.library.entity.house.HouseServiceEntity;
import sh.harold.creative.library.entity.house.HouseServiceSpec;
import sh.harold.creative.library.entity.minestom.MinestomEntityPlatform;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.minestom.MinestomMenuPlatform;
import sh.harold.creative.library.message.Message;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

final class MinestomCooldownHarness implements AutoCloseable {

    private static final String LOCAL_NAMESPACE_ALPHA = "cooldown-lab/local-alpha";
    private static final String LOCAL_NAMESPACE_BETA = "cooldown-lab/local-beta";
    private static final String CONTEXT_NAMESPACE = "cooldown-lab/context";
    private static final String SHARED_NAMESPACE = "cooldown-lab/shared";
    private static final Duration LOCAL_WINDOW = Duration.ofSeconds(5);
    private static final Duration CONTEXT_WINDOW = Duration.ofSeconds(5);
    private static final Duration SHARED_WINDOW = Duration.ofSeconds(8);
    private static final double NPC_OFFSET_X = 12.5;
    private static final double NPC_OFFSET_Z = 1.5;

    private final InstanceContainer instance;
    private final Pos spawn;
    private final MinestomMenuPlatform menus;
    private final MinestomDevHarnessMessages feedback;
    private final MinestomEntityPlatform entityPlatform;
    private final CooldownRegistry registry;

    private HouseServiceEntity npc;

    MinestomCooldownHarness(
            InstanceContainer instance,
            Pos spawn,
            MinestomMenuPlatform menus,
            MinestomDevHarnessMessages feedback
    ) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.spawn = Objects.requireNonNull(spawn, "spawn");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.entityPlatform = new MinestomEntityPlatform();
        this.registry = new InMemoryCooldownRegistry();
    }

    void runKeys(Player player) {
        UUID playerId = player.getUuid();
        clearDemoKeys(playerId);

        LocalSuite local = runLocalSuite(playerId);
        ContextSuite context = runContextSuite(playerId);
        SharedCollisionSuite shared = runSharedCollisionSuite(playerId);

        feedback.send(player, Message.block()
                .title("COOLDOWN KEYS", 0x55FF55)
                .line("This demo proves namespaces, contexts, and shared keys stay separate unless you use the same key on purpose.")
                .blank()
                .bullet("Local namespaces: alpha {alpha}, beta {beta}, alpha repeat {repeat}.",
                        Message.slot("alpha", outcome(local.alpha())),
                        Message.slot("beta", outcome(local.beta())),
                        Message.slot("repeat", outcome(local.alphaRepeat())))
                .bullet("Contexts: alpha {alpha}, beta {beta}, alpha repeat {repeat}.",
                        Message.slot("alpha", outcome(context.alpha())),
                        Message.slot("beta", outcome(context.beta())),
                        Message.slot("repeat", outcome(context.alphaRepeat())))
                .bullet("Shared key: first {first}, second {second}, active window {remaining}.",
                        Message.slot("first", outcome(shared.first())),
                        Message.slot("second", outcome(shared.second())),
                        Message.slot("remaining", formatRemaining(shared.remaining())))
                .build());
    }

    void runLocal(Player player) {
        UUID playerId = player.getUuid();
        LocalSuite local = runLocalSuite(playerId);
        feedback.success(player, "Local namespace alpha accepted.");
        feedback.success(player, "Local namespace beta accepted.");
        feedback.error(player, "Local namespace alpha repeat rejected for {remaining}.",
                Message.slot("remaining", remainingText(local.alphaRepeat())));
    }

    void runShared(Player player) {
        UUID playerId = player.getUuid();
        SharedSuite shared = runSharedSuite(playerId);
        feedback.success(player, "Shared cooldown accepted twice and now remains active for {remaining}.",
                Message.slot("remaining", formatRemaining(shared.remaining())));
    }

    void runContext(Player player) {
        UUID playerId = player.getUuid();
        ContextSuite context = runContextSuite(playerId);
        feedback.success(player, "Context alpha accepted.");
        feedback.success(player, "Context beta accepted.");
        feedback.error(player, "Context alpha repeat rejected for {remaining}.",
                Message.slot("remaining", remainingText(context.alphaRepeat())));
    }

    void openMenu(Player player) {
        menus.open(player, cooldownMenu(player.getUuid()));
        feedback.info(player, "Opened the cooldown lab menu.");
    }

    void resetNpc(Player player) {
        spawnNpc();
        if (player != null) {
            feedback.success(player, "Reset the cooldown keeper NPC near spawn.");
        }
    }

    void clear(Player player) {
        clearDemoKeys(player.getUuid());
        despawnNpc();
        feedback.success(player, "Cleared the cooldown lab keys and removed the cooldown keeper NPC.");
    }

    @Override
    public void close() {
        despawnNpc();
        entityPlatform.close();
        registry.close();
    }

    private Menu cooldownMenu(UUID playerId) {
        return menus.canvas()
                .title("Cooldown Lab")
                .place(20, localButton(playerId))
                .place(22, overviewDisplay(playerId))
                .place(24, sharedButton(playerId))
                .place(29, contextButton(playerId))
                .place(31, clearButton(playerId))
                .build();
    }

    private MenuButton localButton(UUID playerId) {
        return menus.button(Material.HOPPER)
                .name(Component.text("Local Demo", NamedTextColor.GOLD))
                .description("Prove local namespace separation and a rejecting repeat window.")
                .pairs(
                        "Alpha namespace", LOCAL_NAMESPACE_ALPHA,
                        "Beta namespace", LOCAL_NAMESPACE_BETA,
                        "Window", "5 seconds")
                .action(ActionVerb.CONFIRM, "run local demo", context -> {
                    runLocalDemo(playerId);
                    reopenMenu(playerId);
                })
                .build();
    }

    private MenuButton sharedButton(UUID playerId) {
        return menus.button(Material.CLOCK)
                .name(Component.text("Shared Demo", NamedTextColor.AQUA))
                .description("Use the shared server key that the NPC and menu also touch.")
                .pairs(
                        "Namespace", SHARED_NAMESPACE,
                        "Window", "8 seconds")
                .action(ActionVerb.CONFIRM, "run shared demo", context -> {
                    runSharedDemo(playerId);
                    reopenMenu(playerId);
                })
                .build();
    }

    private MenuButton contextButton(UUID playerId) {
        return menus.button(Material.BOOK)
                .name(Component.text("Context Demo", NamedTextColor.LIGHT_PURPLE))
                .description("Prove that the same action splits cleanly by context reference.")
                .pairs(
                        "Context A", "alpha",
                        "Context B", "beta",
                        "Window", "5 seconds")
                .action(ActionVerb.CONFIRM, "run context demo", context -> {
                    runContextDemo(playerId);
                    reopenMenu(playerId);
                })
                .build();
    }

    private MenuButton clearButton(UUID playerId) {
        return menus.button(Material.BARRIER)
                .name(Component.text("Clear", NamedTextColor.RED))
                .description("Clear every cooldown demo key and remove the dedicated NPC.")
                .pairs(
                        "Affects", "local, context, shared, npc",
                        "NPC", npc == null ? "absent" : "spawned")
                .action(ActionVerb.CONFIRM, "clear cooldown lab", context -> {
                    clearDemoKeys(playerId);
                    despawnNpc();
                    Player player = onlinePlayer(playerId);
                    if (player != null) {
                        feedback.success(player, "Cleared the cooldown lab keys and removed the cooldown keeper NPC.");
                    }
                    reopenMenu(playerId);
                })
                .build();
    }

    private MenuDisplayItem overviewDisplay(UUID playerId) {
        return menus.display(Material.COMPASS)
                .name(Component.text("Current State", NamedTextColor.YELLOW))
                .secondary("Live registry snapshot")
                .pair("Local alpha", status(localAlphaKey(playerId)))
                .pair("Local beta", status(localBetaKey(playerId)))
                .pair("Context alpha", status(contextKey(playerId, "alpha")))
                .pair("Context beta", status(contextKey(playerId, "beta")))
                .pair("Shared", status(sharedKey(playerId)))
                .pair("NPC", npc == null ? "absent" : "spawned")
                .build();
    }

    private void runLocalDemo(UUID playerId) {
        LocalSuite local = runLocalSuite(playerId);
        Player player = onlinePlayer(playerId);
        if (player == null) {
            return;
        }
        feedback.success(player, "Local namespace alpha accepted.");
        feedback.success(player, "Local namespace beta accepted.");
        feedback.error(player, "Local namespace alpha repeat rejected for {remaining}.",
                Message.slot("remaining", remainingText(local.alphaRepeat())));
    }

    private void runSharedDemo(UUID playerId) {
        SharedSuite shared = runSharedSuite(playerId);
        Player player = onlinePlayer(playerId);
        if (player == null) {
            return;
        }
        feedback.success(player, "Shared cooldown accepted twice and now remains active for {remaining}.",
                Message.slot("remaining", formatRemaining(shared.remaining())));
    }

    private void runContextDemo(UUID playerId) {
        ContextSuite context = runContextSuite(playerId);
        Player player = onlinePlayer(playerId);
        if (player == null) {
            return;
        }
        feedback.success(player, "Context alpha accepted.");
        feedback.success(player, "Context beta accepted.");
        feedback.error(player, "Context alpha repeat rejected for {remaining}.",
                Message.slot("remaining", remainingText(context.alphaRepeat())));
    }

    private LocalSuite runLocalSuite(UUID playerId) {
        clearLocalKeys(playerId);
        CooldownAcquisition alpha = acquire(localAlphaKey(playerId), CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition beta = acquire(localBetaKey(playerId), CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition alphaRepeat = acquire(localAlphaKey(playerId), CooldownSpec.rejecting(LOCAL_WINDOW));
        return new LocalSuite(alpha, beta, alphaRepeat);
    }

    private ContextSuite runContextSuite(UUID playerId) {
        clearContextKeys(playerId);
        CooldownAcquisition alpha = acquire(contextKey(playerId, "alpha"), CooldownSpec.rejecting(CONTEXT_WINDOW));
        CooldownAcquisition beta = acquire(contextKey(playerId, "beta"), CooldownSpec.rejecting(CONTEXT_WINDOW));
        CooldownAcquisition alphaRepeat = acquire(contextKey(playerId, "alpha"), CooldownSpec.rejecting(CONTEXT_WINDOW));
        return new ContextSuite(alpha, beta, alphaRepeat);
    }

    private SharedSuite runSharedSuite(UUID playerId) {
        CooldownKey key = sharedKey(playerId);
        CooldownAcquisition first = acquire(key, CooldownSpec.extending(SHARED_WINDOW));
        CooldownAcquisition second = acquire(key, CooldownSpec.extending(SHARED_WINDOW));
        Duration remaining = registry.remaining(key).orElseThrow();
        return new SharedSuite(first, second, remaining);
    }

    private SharedCollisionSuite runSharedCollisionSuite(UUID playerId) {
        CooldownKey key = sharedKey(playerId);
        CooldownAcquisition first = acquire(key, CooldownSpec.rejecting(SHARED_WINDOW));
        CooldownAcquisition second = acquire(key, CooldownSpec.rejecting(SHARED_WINDOW));
        Duration remaining = registry.remaining(key).orElseThrow();
        return new SharedCollisionSuite(first, second, remaining);
    }

    private void clearDemoKeys(UUID playerId) {
        clearLocalKeys(playerId);
        clearContextKeys(playerId);
        registry.clear(sharedKey(playerId));
    }

    private void clearLocalKeys(UUID playerId) {
        registry.clear(localAlphaKey(playerId));
        registry.clear(localBetaKey(playerId));
    }

    private void clearContextKeys(UUID playerId) {
        registry.clear(contextKey(playerId, "alpha"));
        registry.clear(contextKey(playerId, "beta"));
    }

    private void spawnNpc() {
        despawnNpc();

        npc = entityPlatform.spawnService(instance, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(transform(spawn.x() + NPC_OFFSET_X, spawn.y(), spawn.z() + NPC_OFFSET_Z, spawn.yaw(), spawn.pitch()))
                        .flags(CommonEntityFlags.builder()
                                .gravity(false)
                                .build())
                        .build())
                .name("&dCooldown Keeper")
                .description("Shared key demo")
                .clickHandler(context -> withInteractor(context.interactor(), player -> runNpcClick(player)))
                .build());
    }

    private void runNpcClick(Player player) {
        CooldownKey key = sharedKey(player.getUuid());
        CooldownAcquisition acquisition = acquire(key, CooldownSpec.rejecting(SHARED_WINDOW));
        if (acquisition instanceof CooldownAcquisition.Accepted) {
            feedback.success(player, "Cooldown keeper accepted the shared key and leaves {remaining} remaining.",
                    Message.slot("remaining", formatRemaining(registry.remaining(key).orElseThrow())));
        } else {
            feedback.error(player, "Cooldown keeper rejected the shared key for {remaining}.",
                    Message.slot("remaining", remainingText(acquisition)));
        }
        menus.open(player, cooldownMenu(player.getUuid()));
    }

    private void despawnNpc() {
        if (npc != null) {
            npc.despawn();
            npc = null;
        }
    }

    private CooldownAcquisition acquire(CooldownKey key, CooldownSpec spec) {
        return registry.acquire(key, spec);
    }

    private String status(CooldownKey key) {
        return registry.remaining(key).map(this::formatRemaining).orElse("idle");
    }

    private String outcome(CooldownAcquisition acquisition) {
        if (acquisition instanceof CooldownAcquisition.Accepted) {
            return "accepted";
        }
        return "rejected for " + remainingText(acquisition);
    }

    private String remainingText(CooldownAcquisition acquisition) {
        if (acquisition instanceof CooldownAcquisition.Rejected rejected) {
            return formatRemaining(rejected.remaining());
        }
        return "accepted";
    }

    private String formatRemaining(Duration remaining) {
        long millis = Math.max(0L, remaining.toMillis());
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0d);
    }

    private Player onlinePlayer(UUID playerId) {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
    }

    private void reopenMenu(UUID playerId) {
        Player player = onlinePlayer(playerId);
        if (player != null) {
            menus.open(player, cooldownMenu(playerId));
        }
    }

    private CooldownKey localAlphaKey(UUID playerId) {
        return CooldownKeys.localPlayer(LOCAL_NAMESPACE_ALPHA, "tap", playerId);
    }

    private CooldownKey localBetaKey(UUID playerId) {
        return CooldownKeys.localPlayer(LOCAL_NAMESPACE_BETA, "tap", playerId);
    }

    private CooldownKey contextKey(UUID playerId, String contextId) {
        CooldownRef context = sh.harold.creative.library.cooldown.CooldownRefs.literal(contextId);
        return CooldownKeys.localPlayer(CONTEXT_NAMESPACE, "tap", playerId, context);
    }

    private CooldownKey sharedKey(UUID playerId) {
        return CooldownKeys.sharedServerPlayer(SHARED_NAMESPACE, "tap", playerId);
    }

    private static EntityTransform transform(double x, double y, double z, float yaw, float pitch) {
        return new EntityTransform(x, y, z, yaw, pitch);
    }

    private void withInteractor(InteractorRef interactor, Consumer<Player> action) {
        Player player = onlinePlayer(interactor.uniqueId());
        if (player != null) {
            action.accept(player);
        }
    }

    private record LocalSuite(CooldownAcquisition alpha, CooldownAcquisition beta, CooldownAcquisition alphaRepeat) {
    }

    private record ContextSuite(CooldownAcquisition alpha, CooldownAcquisition beta, CooldownAcquisition alphaRepeat) {
    }

    private record SharedSuite(CooldownAcquisition first, CooldownAcquisition second, Duration remaining) {
    }

    private record SharedCollisionSuite(CooldownAcquisition first, CooldownAcquisition second, Duration remaining) {
    }
}
