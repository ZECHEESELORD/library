package sh.harold.creative.library.example.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import sh.harold.creative.library.cooldown.CooldownAcquisition;
import sh.harold.creative.library.cooldown.CooldownKey;
import sh.harold.creative.library.cooldown.CooldownKeys;
import sh.harold.creative.library.cooldown.CooldownRef;
import sh.harold.creative.library.cooldown.CooldownRegistry;
import sh.harold.creative.library.cooldown.CooldownScope;
import sh.harold.creative.library.cooldown.CooldownSpec;
import sh.harold.creative.library.cooldown.CooldownRefs;
import sh.harold.creative.library.cooldown.core.InMemoryCooldownRegistry;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuPair;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.ui.value.UiValue;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class PaperCooldownExamples implements AutoCloseable {

    private static final String NAMESPACE = "paper-example.cooldowns";
    private static final String LOCAL_ACTION = "local";
    private static final String SHARED_ACTION = "shared";
    private static final String CONTEXT_ACTION = "context";
    private static final Duration LOCAL_WINDOW = Duration.ofSeconds(5);
    private static final Duration SHARED_WINDOW = Duration.ofSeconds(8);
    private static final CooldownRef CONTEXT_ALPHA = CooldownRefs.literal("alpha");
    private static final CooldownRef CONTEXT_BETA = CooldownRefs.literal("beta");

    private final PaperMenuPlatform menus;
    private final PaperExampleMessages feedback;
    private final CooldownRegistry cooldowns;

    PaperCooldownExamples(PaperMenuPlatform menus, PaperExampleMessages feedback) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.cooldowns = new InMemoryCooldownRegistry();
    }

    String usage() {
        return "/testcooldowns keys|local|shared|context|menu|clear|help";
    }

    void run(Player player, String variant) {
        switch (variant) {
            case "keys" -> runKeys(player);
            case "local" -> runLocal(player);
            case "shared" -> runShared(player);
            case "context" -> runContext(player);
            case "menu" -> openMenu(player);
            case "clear" -> clear(player);
            case "help" -> sendHelp(player);
            default -> feedback.error(player, "Unhandled cooldown variant {variant}.", Message.slot("variant", variant));
        }
    }

    void openMenu(Player player) {
        menus.open(player, menu(player));
    }

    void clear(Player player) {
        clearKnownKeys(player);
        feedback.success(player, "Cleared the cooldown demo keys.");
    }

    @Override
    public void close() {
        cooldowns.close();
    }

    private void runKeys(Player player) {
        clearKnownKeys(player);

        CooldownAcquisition localAlpha = acquire(localAlphaKey(player), CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition localBeta = acquire(localBetaKey(player), CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition localAlphaRepeat = acquire(localAlphaKey(player), CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition sharedFirst = acquire(sharedCollisionKey(player), CooldownSpec.rejecting(SHARED_WINDOW));
        CooldownAcquisition sharedSecond = acquire(sharedCollisionKey(player), CooldownSpec.rejecting(SHARED_WINDOW));
        CooldownAcquisition contextAlpha = acquire(contextKey(player, CONTEXT_ALPHA), CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition contextBeta = acquire(contextKey(player, CONTEXT_BETA), CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition contextAlphaRepeat = acquire(contextKey(player, CONTEXT_ALPHA), CooldownSpec.rejecting(LOCAL_WINDOW));

        feedback.send(player, Message.block()
                .title("COOLDOWN KEYS", 0x55FF55)
                .line("Local namespaces stay separate, shared keys collide on purpose, and context splits related uses.")
                .blank()
                .bullet("local alpha {alpha}, local beta {beta}, alpha repeat {repeat}",
                        Message.slot("alpha", outcomeLabel(localAlpha, localAlphaKey(player), LOCAL_WINDOW)),
                        Message.slot("beta", outcomeLabel(localBeta, localBetaKey(player), LOCAL_WINDOW)),
                        Message.slot("repeat", outcomeLabel(localAlphaRepeat, localAlphaKey(player), LOCAL_WINDOW)))
                .bullet("shared key accepted, then rejected on the immediate second acquire: {first} / {second}",
                        Message.slot("first", outcomeLabel(sharedFirst, sharedCollisionKey(player), SHARED_WINDOW)),
                        Message.slot("second", outcomeLabel(sharedSecond, sharedCollisionKey(player), SHARED_WINDOW)))
                .bullet("context alpha {alpha}, context beta {beta}, alpha repeat {repeat}",
                        Message.slot("alpha", outcomeLabel(contextAlpha, contextKey(player, CONTEXT_ALPHA), LOCAL_WINDOW)),
                        Message.slot("beta", outcomeLabel(contextBeta, contextKey(player, CONTEXT_BETA), LOCAL_WINDOW)),
                        Message.slot("repeat", outcomeLabel(contextAlphaRepeat, contextKey(player, CONTEXT_ALPHA), LOCAL_WINDOW)))
                .build());
    }

    private void runLocal(Player player) {
        CooldownKey key = localKey(player);
        CooldownAcquisition acquisition = acquire(key, CooldownSpec.rejecting(LOCAL_WINDOW));
        if (acquisition instanceof CooldownAcquisition.Accepted) {
            feedback.success(player, "Local cooldown accepted for {remaining}.",
                    Message.slot("remaining", outcomeLabel(acquisition, key, LOCAL_WINDOW)));
            return;
        }
        feedback.info(player, "Local cooldown is still active for {remaining}.",
                Message.slot("remaining", outcomeLabel(acquisition, key, LOCAL_WINDOW)));
    }

    private void runShared(Player player) {
        CooldownKey key = sharedKey(player);
        CooldownAcquisition acquisition = acquire(key, CooldownSpec.extending(SHARED_WINDOW));
        feedback.success(player, "Shared cooldown accepted and now remains active for {remaining}.",
                Message.slot("remaining", outcomeLabel(acquisition, key, SHARED_WINDOW)));
    }

    private void runContext(Player player) {
        CooldownKey alphaKey = contextKey(player, CONTEXT_ALPHA);
        CooldownKey betaKey = contextKey(player, CONTEXT_BETA);
        CooldownAcquisition alpha = acquire(alphaKey, CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition beta = acquire(betaKey, CooldownSpec.rejecting(LOCAL_WINDOW));
        CooldownAcquisition alphaRepeat = acquire(alphaKey, CooldownSpec.rejecting(LOCAL_WINDOW));

        feedback.send(player, Message.block()
                .title("CONTEXT SPLIT", 0xFF55FF)
                .line("Same scope, namespace, name, and subject. Different context refs keep the keys separate.")
                .blank()
                .bullet("context alpha: {alpha}",
                        Message.slot("alpha", outcomeLabel(alpha, alphaKey, LOCAL_WINDOW)))
                .bullet("context beta: {beta}",
                        Message.slot("beta", outcomeLabel(beta, betaKey, LOCAL_WINDOW)))
                .bullet("context alpha repeat: {repeat}",
                        Message.slot("repeat", outcomeLabel(alphaRepeat, alphaKey, LOCAL_WINDOW)))
                .build());
    }

    private void sendHelp(Player player) {
        feedback.info(player, "Use {command} keys|local|shared|context|menu|clear.",
                Message.slot("command", feedback.command("/testcooldowns")));
    }

    private CooldownAcquisition acquire(CooldownKey key, CooldownSpec spec) {
        return cooldowns.acquire(key, spec);
    }

    private void clearKnownKeys(Player player) {
        cooldowns.clear(localAlphaKey(player));
        cooldowns.clear(localBetaKey(player));
        cooldowns.clear(localKey(player));
        cooldowns.clear(sharedKey(player));
        cooldowns.clear(sharedCollisionKey(player));
        cooldowns.clear(contextKey(player, CONTEXT_ALPHA));
        cooldowns.clear(contextKey(player, CONTEXT_BETA));
    }

    private Menu menu(Player player) {
        return menus.canvas()
                .title(FakeSkyBlockMenuTitles.special("Cooldown Lab"))
                .place(13, statusCard(player))
                .place(20, localButton(player))
                .place(22, sharedButton(player))
                .place(24, contextButton(player))
                .place(31, resetButton(player))
                .build();
    }

    private MenuDisplayItem statusCard(Player player) {
        return menus.display(Material.CLOCK)
                .name(FakeSkyBlockMenuTitles.normal("Cooldown Status"))
                .description("Claim a key, then reopen the menu to see the live timer update.")
                        .pairs(
                        MenuPair.of("Local", statusValue(localKey(player))),
                        MenuPair.of("Shared", statusValue(sharedKey(player))),
                        MenuPair.of("Context A", statusValue(contextKey(player, CONTEXT_ALPHA))),
                        MenuPair.of("Context B", statusValue(contextKey(player, CONTEXT_BETA))))
                .build();
    }

    private MenuButton localButton(Player player) {
        return menus.button(Material.LIME_DYE)
                .name(FakeSkyBlockMenuTitles.success("Local Key"))
                .description("Use the rejecting local cooldown twice to prove the repeat is blocked.")
                .action(ActionVerb.CLAIM, context -> {
                    runLocal(player);
                    reopen(player);
                })
                .build();
    }

    private MenuButton sharedButton(Player player) {
        return menus.button(Material.CLOCK)
                .name(FakeSkyBlockMenuTitles.perk("Shared Key"))
                .description("Use the extending shared cooldown twice to prove the timer refreshes.")
                .action(ActionVerb.CLAIM, context -> {
                    runShared(player);
                    reopen(player);
                })
                .build();
    }

    private MenuButton contextButton(Player player) {
        return menus.button(Material.NAME_TAG)
                .name(FakeSkyBlockMenuTitles.special("Context Split"))
                .description("Use two context refs on the same cooldown shape to prove they stay separate.")
                .action(ActionVerb.CLAIM, context -> {
                    runContext(player);
                    reopen(player);
                })
                .build();
    }

    private MenuButton resetButton(Player player) {
        return menus.button(Material.BARRIER)
                .name(FakeSkyBlockMenuTitles.danger("Reset Keys"))
                .description("Clear every key used by this cooldown harness.")
                .action(ActionVerb.MANAGE, "reset", context -> {
                    clear(player);
                    reopen(player);
                })
                .build();
    }

    private void reopen(Player player) {
        menus.open(player, menu(player));
    }

    private UiValue statusValue(CooldownKey key) {
        Optional<Duration> remaining = cooldowns.remaining(key);
        if (remaining.isEmpty()) {
            return FakeSkyBlockMenuValues.ready("Ready");
        }
        return FakeSkyBlockMenuValues.tracked(formatDuration(remaining.get()));
    }

    private String outcomeLabel(CooldownAcquisition acquisition, CooldownKey key, Duration window) {
        if (acquisition instanceof CooldownAcquisition.Accepted) {
            return "accepted for " + formatDuration(currentRemaining(key, window));
        }
        if (acquisition instanceof CooldownAcquisition.Rejected rejected) {
            return "rejected with " + formatDuration(rejected.remaining()) + " remaining";
        }
        return "unknown";
    }

    private Duration currentRemaining(CooldownKey key, Duration fallback) {
        return cooldowns.remaining(key).orElse(fallback);
    }

    private CooldownKey localAlphaKey(Player player) {
        return CooldownKeys.localPlayer(NAMESPACE + ".local-alpha", LOCAL_ACTION, player.getUniqueId());
    }

    private CooldownKey localBetaKey(Player player) {
        return CooldownKeys.localPlayer(NAMESPACE + ".local-beta", LOCAL_ACTION, player.getUniqueId());
    }

    private CooldownKey localKey(Player player) {
        return CooldownKeys.localPlayer(NAMESPACE + ".local", LOCAL_ACTION, player.getUniqueId());
    }

    private CooldownKey sharedCollisionKey(Player player) {
        return CooldownKeys.sharedServerPlayer(NAMESPACE + ".shared-collision", SHARED_ACTION, player.getUniqueId());
    }

    private CooldownKey sharedKey(Player player) {
        return CooldownKeys.sharedServerPlayer(NAMESPACE + ".shared", SHARED_ACTION, player.getUniqueId());
    }

    private CooldownKey contextKey(Player player, CooldownRef context) {
        return CooldownKeys.of(CooldownScope.LOCAL, NAMESPACE + ".context", CONTEXT_ACTION, CooldownRefs.player(player.getUniqueId()), context);
    }

    private String formatDuration(Duration duration) {
        long millis = Math.max(0L, duration.toMillis());
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }
}
