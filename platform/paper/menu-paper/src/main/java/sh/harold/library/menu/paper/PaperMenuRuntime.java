package sh.harold.library.menu.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.math.Position;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyDestination;
import sh.harold.library.menu.MenuCustodyFailure;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuInteraction;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.MenuSlotAction;
import sh.harold.library.menu.MenuStack;
import sh.harold.library.menu.MenuTraceController;
import sh.harold.library.menu.MenuViewerSlot;
import sh.harold.library.menu.ReactiveMenuEffect;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveTextPromptMode;
import sh.harold.library.menu.ReactiveTextPromptRequest;
import sh.harold.library.menu.core.MenuCustodyLedger;
import sh.harold.library.menu.core.MenuTrace;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.MenuTickHandle;
import sh.harold.library.menu.core.MenuTickScheduler;
import sh.harold.library.sound.SoundCueService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class PaperMenuRuntime implements AutoCloseable {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final long TEXT_PROMPT_TIMEOUT_TICKS = 4L * 60L * 20L;

    private final Map<UUID, PaperMenuSession> sessions = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private final PaperMenuAccess access;
    private final Function<UUID, Player> playerLookup;
    private final PaperMenuSlotRenderer renderer;
    private final SoundCueService sounds;
    private final PaperMenuTaskScheduler taskScheduler;
    private final MenuTraceController traceController;
    private final Consumer<String> traceSink;
    private final PaperVirtualSignSupport virtualSigns;
    private final PaperDialogPromptSupport dialogPrompts;
    private final Map<UUID, PendingTextPrompt> prompts = new ConcurrentHashMap<>();
    private final ThreadLocal<PaperMenuSession> userCallbackSession = new ThreadLocal<>();
    private volatile boolean closed;
    private volatile boolean staleInventoryGuardRequired;

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer, SoundCueService sounds) {
        this(access, playerLookup, renderer, sounds, MenuTickScheduler.unsupported(),
                action -> {
                    action.run();
                    return MenuTickHandle.noop();
                },
                new MenuTraceController(), message -> { });
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler) {
        this(access, playerLookup, renderer, sounds, tickScheduler,
                action -> {
                    action.run();
                    return MenuTickHandle.noop();
                },
                new MenuTraceController(), message -> { });
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler, Consumer<Runnable> nextTickScheduler) {
        this(access, playerLookup, renderer, sounds, tickScheduler,
                action -> {
                    nextTickScheduler.accept(action);
                    return MenuTickHandle.noop();
                },
                new MenuTraceController(), message -> { });
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler, Function<Runnable, MenuTickHandle> nextTickScheduler) {
        this(access, playerLookup, renderer, sounds, tickScheduler, nextTickScheduler,
                new MenuTraceController(), message -> { });
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler, Function<Runnable, MenuTickHandle> nextTickScheduler,
                     PaperVirtualSignSupport virtualSigns) {
        this(access, playerLookup, renderer, sounds, tickScheduler, nextTickScheduler,
                new MenuTraceController(), message -> { }, virtualSigns, PaperDialogPromptSupport.live());
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler, Consumer<Runnable> nextTickScheduler,
                     MenuTraceController traceController, Consumer<String> traceSink) {
        this(access, playerLookup, renderer, sounds, tickScheduler,
                action -> {
                    nextTickScheduler.accept(action);
                    return MenuTickHandle.noop();
                },
                traceController, traceSink);
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler, Function<Runnable, MenuTickHandle> nextTickScheduler,
                     MenuTraceController traceController, Consumer<String> traceSink) {
        this(access, playerLookup, renderer, sounds, tickScheduler, nextTickScheduler,
                traceController, traceSink, PaperVirtualSignSupport.live(), PaperDialogPromptSupport.live());
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler, Function<Runnable, MenuTickHandle> nextTickScheduler,
                     MenuTraceController traceController, Consumer<String> traceSink, PaperVirtualSignSupport virtualSigns) {
        this(access, playerLookup, renderer, sounds, tickScheduler, nextTickScheduler,
                traceController, traceSink, virtualSigns, PaperDialogPromptSupport.live());
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler, Function<Runnable, MenuTickHandle> nextTickScheduler,
                     MenuTraceController traceController, Consumer<String> traceSink, PaperVirtualSignSupport virtualSigns,
                     PaperDialogPromptSupport dialogPrompts) {
        this.access = Objects.requireNonNull(access, "access");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.taskScheduler = PaperMenuTaskScheduler.testing(tickScheduler, nextTickScheduler);
        this.traceController = Objects.requireNonNull(traceController, "traceController");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
        this.virtualSigns = Objects.requireNonNull(virtualSigns, "virtualSigns");
        this.dialogPrompts = Objects.requireNonNull(dialogPrompts, "dialogPrompts");
    }

    PaperMenuRuntime(
            PaperMenuAccess access,
            Function<UUID, Player> playerLookup,
            PaperMenuSlotRenderer renderer,
            SoundCueService sounds,
            PaperMenuTaskScheduler taskScheduler,
            MenuTraceController traceController,
            Consumer<String> traceSink
    ) {
        this(access, playerLookup, renderer, sounds, taskScheduler, traceController, traceSink,
                PaperVirtualSignSupport.live(), PaperDialogPromptSupport.live());
    }

    PaperMenuRuntime(
            PaperMenuAccess access,
            Function<UUID, Player> playerLookup,
            PaperMenuSlotRenderer renderer,
            SoundCueService sounds,
            PaperMenuTaskScheduler taskScheduler,
            MenuTraceController traceController,
            Consumer<String> traceSink,
            PaperVirtualSignSupport virtualSigns,
            PaperDialogPromptSupport dialogPrompts
    ) {
        this.access = Objects.requireNonNull(access, "access");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.traceController = Objects.requireNonNull(traceController, "traceController");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
        this.virtualSigns = Objects.requireNonNull(virtualSigns, "virtualSigns");
        this.dialogPrompts = Objects.requireNonNull(dialogPrompts, "dialogPrompts");
    }

    void open(Player player, MenuDefinition menu) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        if (closed) {
            return;
        }
        PaperMenuSession callbackSession = userCallbackSession.get();
        if (callbackSession != null
                && callbackSession.viewerId().equals(player.getUniqueId())
                && callbackSession.deferLifecycle(() -> open(player, menu))) {
            return;
        }
        trace(player, "open", () -> {
            if (closed) {
                return;
            }
            UUID viewerId = player.getUniqueId();
            PaperMenuSession previous = sessions.get(viewerId);
            if (previous != null) {
                if (!drainCustodyForTransition(previous, MenuCustodyGesture.SettleReason.CLOSE)) {
                    return;
                }
            }
            PaperMenuSession session;
            PaperMenuSession.PreparedInventory prepared;
            try {
                session = MenuTrace.time("runtime.createSession",
                        () -> new PaperMenuSession(this, viewerId, new MenuSessionState(menu)));
                MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
                prepared = session.prepareCurrent();
                if (previous == null) {
                    access.openInventory(player, prepared.inventory());
                } else {
                    openReplacementInventory(previous, player, prepared.inventory());
                }
                session.commitInitial(prepared);
            } catch (RuntimeException exception) {
                if (previous != null) {
                    restoreInventoryAfterFailedTransition(previous, player);
                    restoreSettledCustodyView(previous);
                }
                return;
            }
            synchronized (lifecycleLock) {
                if (closed) {
                    session.detach(player);
                    if (access.topInventory(player) == prepared.inventory()) {
                        access.closeInventory(player);
                    }
                    if (previous != null) {
                        previous.endCustodyTransition();
                    }
                    return;
                }
                sessions.put(viewerId, session);
            }
            if (previous != null) {
                MenuTrace.time("runtime.detachPrevious", () -> previous.detach(player));
                previous.endCustodyTransition();
            }
            MenuTrace.time("runtime.show", () -> activateCommittedSession(session, player));
        });
    }

    void onInventoryClick(InventoryClickEvent event) {
        inInventoryInteraction(event.getView().getTopInventory(), () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (ownedSession(topInventory) == null) {
                return;
            }
            event.setCancelled(true);
            if (closed) {
                closeStaleInventoryOnNextTick(ownedSession(topInventory), event.getWhoClicked());
                return;
            }

            PaperMenuSession session = session(topInventory);
            if (session == null) {
                return;
            }

            HumanEntity whoClicked = event.getWhoClicked();
            if (!(whoClicked instanceof Player player) || sessions.get(player.getUniqueId()) != session || !session.matches(player, topInventory)) {
                return;
            }
            if (session.custodyTransitioning()) {
                return;
            }
            if (session.state().reactive()) {
                trace(player, "click", () -> {
                    MenuTrace.field("path", "reactive");
                    MenuTrace.field("slot", event.getRawSlot());
                    MenuTrace.title(session.title());

                    int rawSlot = event.getRawSlot();
                    if (session.custodyEnabled() && handleCustodyClick(session, player, event, topInventory, rawSlot)) {
                        return;
                    }

                    if (rawSlot < 0) {
                        return;
                    }
                    if (rawSlot < topInventory.getSize()) {
                        MenuTrace.time("runtime.handleReactiveTopClick", () -> handleReactiveTopClick(session, player, rawSlot, event));
                        return;
                    }
                });
                return;
            }

            int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
                return;
            }

            MenuClick click = toCompiledMenuClick(event.getClick());
            if (click == null) {
                return;
            }

            MenuInteraction interaction = session.state().interaction(rawSlot, click).orElse(null);
            if (interaction == null) {
                return;
            }

            trace(player, "click", () -> {
                MenuTrace.field("path", "compiled");
                MenuTrace.field("slot", rawSlot);
                MenuTrace.field("button", click);
                MenuTrace.title(session.title());
                if (!allowInput(session, new CompiledClickInput(rawSlot, click))) {
                    return;
                }

                MenuTrace.time("runtime.handleDirectInteraction", () -> handleDirectInteraction(session, player, click, interaction));
            });
        });
    }

    void onInventoryDrag(InventoryDragEvent event) {
        inInventoryInteraction(event.getView().getTopInventory(), () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (ownedSession(topInventory) == null) {
                return;
            }
            event.setCancelled(true);
            if (closed) {
                closeStaleInventoryOnNextTick(ownedSession(topInventory), event.getWhoClicked());
                return;
            }

            PaperMenuSession session = session(topInventory);
            if (session == null || !session.state().reactive()) {
                return;
            }
            HumanEntity whoClicked = event.getWhoClicked();
            if (!(whoClicked instanceof Player player) || sessions.get(player.getUniqueId()) != session || !session.matches(player, topInventory)) {
                return;
            }
            if (session.custodyTransitioning()) {
                return;
            }
            if (session.custodyEnabled()) {
                handleCustodyDrag(session, player, event, topInventory);
            }
        });
    }

    void onInventoryClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        PaperMenuSession session = session(topInventory);
        if (session == null) {
            return;
        }

        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player) || sessions.get(player.getUniqueId()) != session || !session.matches(player, topInventory)) {
            return;
        }
        trace(player, "close", () -> {
            MenuTrace.title(session.title());
            if (advancePromptAfterClose(player.getUniqueId(), session, topInventory)) {
                return;
            }
            if (session.nativeInventoryReplacementInProgress()) {
                return;
            }
            session.cancelQueuedInventoryReplacement();
            if (!session.beginCustodyTransition()) {
                return;
            }
            if (!settleCustody(
                    session,
                    player,
                    true,
                    MenuCustodyGesture.SettleReason.CLOSE)) {
                retireCustody(session);
            }
            if (sessions.remove(player.getUniqueId(), session)) {
                MenuTrace.time("runtime.sessionDetach", () -> session.detach(player));
            }
            session.endCustodyTransition();
        });
    }

    void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingTextPrompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null || prompt.session() != sessions.get(player.getUniqueId())
                || prompt.mode() != ReactiveTextPromptMode.CHAT
                || prompt.phase() != PendingTextPromptPhase.ACTIVE) {
            return;
        }
        event.setCancelled(true);
        String message = PLAIN_TEXT.serialize(event.message());
        scheduleNextTick(prompt.session(), MenuTrace.propagate(() -> completePrompt(prompt,
                "cancel".equalsIgnoreCase(message.trim())
                        ? new ReactiveMenuInput.TextPromptCancelled(prompt.request().key(), ReactiveTextPromptMode.CHAT)
                        : new ReactiveMenuInput.TextPromptSubmitted(prompt.request().key(), message, ReactiveTextPromptMode.CHAT))));
    }

    void onUncheckedSignChange(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        PendingTextPrompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null || prompt.session() != sessions.get(player.getUniqueId())
                || prompt.mode() != ReactiveTextPromptMode.SIGN
                || prompt.phase() != PendingTextPromptPhase.ACTIVE) {
            return;
        }
        if (event.getSide() != Side.FRONT || !sameBlock(prompt.signLocation(), event.getEditedBlockPosition())) {
            return;
        }
        event.setCancelled(true);
        List<Component> lines = event.lines();
        String value = lines.isEmpty() ? "" : flatten(lines.getFirst());
        completePrompt(prompt, new ReactiveMenuInput.TextPromptSubmitted(prompt.request().key(), value, ReactiveTextPromptMode.SIGN));
    }

    void onPlayerDisconnect(Player player) {
        PendingTextPrompt prompt = removePrompt(player.getUniqueId());
        if (prompt != null) {
            restorePromptClientBlock(prompt, player);
        }
        PaperMenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.endCustodyTransition();
            if (session.beginCustodyTransition()) {
                if (!settleCustody(
                        session,
                        player,
                        false,
                        MenuCustodyGesture.SettleReason.DISCONNECT)) {
                    retireCustody(session);
                }
                if (sessions.remove(player.getUniqueId(), session)) {
                    session.detach(player);
                }
                session.endCustodyTransition();
            }
        }
    }

    void onPlayerDeath(Player player, boolean keepInventory, List<ItemStack> drops) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(drops, "drops");
        PendingTextPrompt prompt = removePrompt(player.getUniqueId());
        if (prompt != null) {
            restorePromptClientBlock(prompt, player);
        }
        PaperMenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.cancelQueuedInventoryReplacement();
        session.endCustodyTransition();
        if (!session.beginCustodyTransition()) {
            return;
        }
        boolean settled;
        try {
            settled = keepInventory
                    ? settleCustody(session, player, false, MenuCustodyGesture.SettleReason.DEATH)
                    : settleCustodyToDeathDrops(session, player, drops);
        } catch (RuntimeException exception) {
            settled = false;
        }
        if (!settled) {
            retireCustody(session);
        }
        if (sessions.remove(player.getUniqueId(), session)) {
            try {
                session.detach(player);
            } catch (RuntimeException ignored) {
            }
        }
        session.endCustodyTransition();
    }

    void onTick(PaperMenuSession session) {
        if (closed
                || sessions.get(session.viewerId()) != session
                || session.custodyTransitioning()
                || promptBlocksInventoryOpen(session)) {
            return;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (player == null) {
            return;
        }
        try {
            trace(player, "tick", () -> {
                MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
                MenuTrace.title(session.title());
                UserCallbackFence callbackFence = captureUserCallbackFence(session);
                long revision = session.state().revision();
                List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.stateTick",
                        () -> invokeUserCallback(session, session.state()::tick));
                if (!userCallbackFenceHolds(session, player, callbackFence, false, true, false)) {
                    return;
                }
                boolean handled = MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects));
                if (!handled && session.state().revision() != revision) {
                    MenuTrace.time("session.refresh", () -> session.refresh(player));
                }
            });
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
        }
    }

    void refresh(PaperMenuSession session) {
        if (closed
                || sessions.get(session.viewerId()) != session
                || session.custodyTransitioning()
                || promptBlocksInventoryOpen(session)) {
            return;
        }
        if (session.deferLifecycle(() -> refresh(session))) {
            return;
        }
        session.state().invalidateView();
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            try {
                MenuTrace.time("session.refresh", () -> session.refresh(player));
            } catch (RuntimeException exception) {
                quarantineAfterFailure(session, player);
            }
        }
    }

    void replace(PaperMenuSession session, MenuDefinition menu) {
        if (closed || sessions.get(session.viewerId()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> replace(session, menu))) {
            return;
        }
        MenuTrace.time("runtime.replace", () -> {
            if (!drainCustodyForTransition(session, MenuCustodyGesture.SettleReason.NAVIGATE)) {
                return;
            }
            try {
                session.state().prepareOpenChild(menu).ifPresentOrElse(
                        transition -> applyPreparedTransition(session, transition),
                        () -> restoreSettledCustodyView(session));
            } catch (RuntimeException exception) {
                restoreSettledCustodyView(session);
            }
        });
    }

    void replaceCurrent(PaperMenuSession session, MenuDefinition menu) {
        if (closed || sessions.get(session.viewerId()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> replaceCurrent(session, menu))) {
            return;
        }
        MenuTrace.time("runtime.replaceCurrent", () -> {
            if (!drainCustodyForTransition(session, MenuCustodyGesture.SettleReason.NAVIGATE)) {
                return;
            }
            try {
                applyPreparedTransition(session, session.state().prepareReplaceCurrent(menu));
            } catch (RuntimeException exception) {
                restoreSettledCustodyView(session);
            }
        });
    }

    void back(PaperMenuSession session) {
        if (closed || sessions.get(session.viewerId()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> back(session))) {
            return;
        }
        MenuTrace.time("runtime.back", () -> {
            if (!drainCustodyForTransition(session, MenuCustodyGesture.SettleReason.NAVIGATE)) {
                return;
            }
            session.state().prepareBack().ifPresentOrElse(
                    transition -> applyPreparedTransition(session, transition),
                    () -> restoreSettledCustodyView(session));
        });
    }

    void close(PaperMenuSession session) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> close(session))) {
            return;
        }
        if (!drainCustodyForTransition(session, MenuCustodyGesture.SettleReason.CLOSE)) {
            return;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (!sessions.remove(session.viewerId(), session)) {
            session.endCustodyTransition();
            return;
        }
        PendingTextPrompt prompt = removePrompt(session.viewerId());
        if (prompt != null && player != null) {
            restorePromptClientBlock(prompt, player);
        }
        MenuTrace.time("runtime.close", () -> session.detach(player));
        session.endCustodyTransition();
        if (player != null) {
            if (session.shouldDeferInventoryTransitions()) {
                Inventory closingInventory = session.inventory();
                scheduleNextTick(session, MenuTrace.propagate(() -> {
                    if (access.topInventory(player) == closingInventory) {
                        MenuTrace.time("runtime.inventoryClose", () -> access.closeInventory(player));
                    }
                }));
            } else {
                MenuTrace.time("runtime.inventoryClose", () -> access.closeInventory(player));
            }
        }
    }

    private void applyPreparedTransition(
            PaperMenuSession session,
            MenuSessionState.PreparedTransition transition
    ) {
        Runnable action = MenuTrace.propagate(() -> performPreparedTransition(session, transition));
        if (session.shouldDeferInventoryTransitions()) {
            session.queueInventoryReplacement();
            try {
                scheduleNextTick(session, action);
            } catch (RuntimeException exception) {
                session.endCustodyTransition();
                throw exception;
            }
            return;
        }
        action.run();
    }

    private void performPreparedTransition(
            PaperMenuSession session,
            MenuSessionState.PreparedTransition transition
    ) {
        Player player = playerLookup.apply(session.viewerId());
        if (player == null || sessions.get(session.viewerId()) != session) {
            session.endCustodyTransition();
            return;
        }
        PaperMenuSession.PreparedInventory prepared;
        UserCallbackFence callbackFence = captureUserCallbackFence(session);
        try {
            prepared = session.prepareTransition(transition);
            if (!userCallbackFenceHolds(session, player, callbackFence, true, false, true)) {
                session.endCustodyTransition();
                return;
            }
            openReplacementInventory(session, player, prepared.inventory());
            session.commitTransition(prepared);
        } catch (RuntimeException exception) {
            restoreInventoryAfterFailedTransition(session, player);
            restoreSettledCustodyView(session);
            return;
        }
        session.endCustodyTransition();
        activateCommittedSession(session, player);
    }

    private void restoreInventoryAfterFailedTransition(PaperMenuSession session, Player player) {
        Inventory inventory = session.inventory();
        if (inventory == null || access.topInventory(player) == inventory) {
            return;
        }
        try {
            access.openInventory(player, inventory);
        } catch (RuntimeException ignored) {
        }
    }

    private void restoreSettledCustodyView(PaperMenuSession session) {
        session.endCustodyTransition();
        Player player = playerLookup.apply(session.viewerId());
        if (closed
                || player == null
                || sessions.get(session.viewerId()) != session
                || access.topInventory(player) != session.inventory()) {
            return;
        }
        try {
            MenuTrace.time("session.restoreSettledCustodyView",
                    () -> session.restoreSettledCustodyView(player));
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
        }
    }

    private void openReplacementInventory(
            PaperMenuSession session,
            Player player,
            Inventory replacement
    ) {
        session.beginNativeInventoryReplacement();
        try {
            access.openInventory(player, replacement);
        } finally {
            session.endNativeInventoryReplacement();
        }
    }

    private void activateCommittedSession(PaperMenuSession session, Player player) {
        try {
            if (!session.state().reactive()) {
                return;
            }
            UserCallbackFence callbackFence = captureUserCallbackFence(session);
            long revision = session.state().revision();
            List<ReactiveMenuEffect> effects = invokeUserCallback(session, session.state()::opened);
            if (!userCallbackFenceHolds(session, player, callbackFence, true, true, false)) {
                return;
            }
            boolean handled = applyEffects(session, player, effects);
            if (!handled && session.state().revision() != revision) {
                session.refresh(player);
            }
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
        }
    }

    void render(Inventory inventory, List<MenuSlot> previousSlots, List<MenuSlot> nextSlots) {
        render(inventory, previousSlots, nextSlots, Set.of());
    }

    void render(
            PaperMenuSession session,
            Inventory inventory,
            List<MenuSlot> previousSlots,
            List<MenuSlot> nextSlots
    ) {
        Set<Integer> occupiedTargets = new HashSet<>();
        session.custody().targetSlots().forEach((key, slot) -> {
            if (session.custody().target(key).isPresent()) {
                occupiedTargets.add(slot);
            }
        });
        render(inventory, previousSlots, nextSlots, occupiedTargets);
    }

    private void render(
            Inventory inventory,
            List<MenuSlot> previousSlots,
            List<MenuSlot> nextSlots,
            Set<Integer> skippedSlots
    ) {
        long started = System.nanoTime();
        int changedSlots = 0;
        for (int slot = 0; slot < nextSlots.size(); slot++) {
            if (skippedSlots.contains(slot)) {
                continue;
            }
            MenuSlot nextSlot = nextSlots.get(slot);
            if (previousSlots == null || !nextSlot.equals(previousSlots.get(slot))) {
                changedSlots++;
                int renderedSlot = slot;
                long renderStarted = System.nanoTime();
                ItemStack rendered = renderer.render(nextSlot);
                long renderElapsed = System.nanoTime() - renderStarted;
                MenuTrace.addDuration("runtime.slotRender", renderElapsed);
                MenuTrace.detailIfSlow("slot-render", renderElapsed,
                        () -> "slot=" + renderedSlot + " title=" + flatten(nextSlot.title()));

                long patchStarted = System.nanoTime();
                inventory.setItem(renderedSlot, rendered);
                long patchElapsed = System.nanoTime() - patchStarted;
                MenuTrace.addDuration("runtime.slotPatch", patchElapsed);
                MenuTrace.detailIfSlow("slot-patch", patchElapsed,
                        () -> "slot=" + renderedSlot + " title=" + flatten(nextSlot.title()));
            }
        }
        MenuTrace.setCount("changedSlots", changedSlots);
        MenuTrace.addDuration("runtime.inventoryPatch", System.nanoTime() - started);
    }

    boolean validateCustodyForRender(PaperMenuSession session, Player player, Inventory inventory) {
        if (!session.custodyEnabled()) {
            return true;
        }
        try {
            if (session.custody().cursor().isPresent()
                    && !sameItem(player.getItemOnCursor(), session.custody().cursor().orElseThrow().nativeItem())) {
                quarantineUncertainNativeState(session, player);
                return false;
            }
            for (Map.Entry<String, Integer> target : session.custody().targetSlots().entrySet()) {
                ItemStack expected = session.custody().target(target.getKey())
                        .map(MenuCustodyLedger.Entry::nativeItem)
                        .orElseGet(() -> renderer.render(session.renderedSlots().get(target.getValue())));
                if (!sameItem(inventory.getItem(target.getValue()), expected)) {
                    quarantineUncertainNativeState(session, player);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
            return false;
        }
    }

    PaperMenuAccess access() {
        return access;
    }

    MenuTickHandle scheduleTicks(PaperMenuSession session, long intervalTicks, Runnable action) {
        Player player = playerLookup.apply(session.viewerId());
        if (player == null) {
            return MenuTickHandle.noop();
        }
        return taskScheduler.schedule(player, intervalTicks, Objects.requireNonNull(action, "action"));
    }

    MenuTickHandle scheduleNextTick(PaperMenuSession session, Runnable action) {
        Player player = playerLookup.apply(session.viewerId());
        if (player == null) {
            return MenuTickHandle.noop();
        }
        return taskScheduler.next(player, Objects.requireNonNull(action, "action"));
    }

    void scheduleDeferredLifecycle(PaperMenuSession session, List<Runnable> actions) {
        if (actions.isEmpty()) {
            return;
        }
        try {
            scheduleNextTick(session, () -> {
                for (Runnable action : actions) {
                    if (closed || sessions.get(session.viewerId()) != session) {
                        return;
                    }
                    action.run();
                }
            });
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, playerLookup.apply(session.viewerId()));
        }
    }

    <T> T invokeUserCallback(PaperMenuSession session, Supplier<T> callback) {
        PaperMenuSession previous = userCallbackSession.get();
        userCallbackSession.set(session);
        try {
            return session.invokeUserCallback(callback);
        } finally {
            if (previous == null) {
                userCallbackSession.remove();
            } else {
                userCallbackSession.set(previous);
            }
        }
    }

    private UserCallbackFence captureUserCallbackFence(PaperMenuSession session) {
        return new UserCallbackFence(
                session.inventory(),
                session.custody(),
                session.actionVersion(),
                session.lifecycleGeneration(),
                session.state().revision());
    }

    private boolean userCallbackFenceHolds(
            PaperMenuSession session,
            Player player,
            UserCallbackFence expected,
            boolean requireActiveTop,
            boolean requireNoTransition,
            boolean requireSameStateRevision
    ) {
        if (closed
                || player == null
                || sessions.get(session.viewerId()) != session
                || session.inventory() != expected.inventory()
                || session.custody() != expected.custody()
                || session.actionVersion() != expected.actionVersion()
                || session.lifecycleGeneration() != expected.lifecycleGeneration()
                || requireSameStateRevision && session.state().revision() != expected.stateRevision()
                || requireNoTransition && session.custodyTransitioning()) {
            return false;
        }
        return !requireActiveTop
                || expected.inventory() != null
                && session.matches(player, expected.inventory())
                && access.topInventory(player) == expected.inventory();
    }

    boolean userCallbackFenceHolds(
            PaperMenuSession session,
            Player player,
            Inventory expectedInventory,
            MenuCustodyLedger<ItemStack> expectedCustody,
            long expectedActionVersion,
            long expectedLifecycleGeneration,
            boolean requireActiveTop
    ) {
        return userCallbackFenceHolds(
                session,
                player,
                new UserCallbackFence(
                        expectedInventory,
                        expectedCustody,
                        expectedActionVersion,
                        expectedLifecycleGeneration,
                        session.state().revision()),
                requireActiveTop,
                true,
                false);
    }

    private record UserCallbackFence(
            Inventory inventory,
            MenuCustodyLedger<ItemStack> custody,
            long actionVersion,
            long lifecycleGeneration,
            long stateRevision
    ) {
    }

    @Override
    public void close() {
        List<PaperMenuSession> closingSessions;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            closingSessions = List.copyOf(sessions.values());
        }
        closingSessions.forEach(this::shutdownSession);
        sessions.clear();
    }

    boolean requiresStaleInventoryGuard() {
        return staleInventoryGuardRequired;
    }

    private void shutdownSession(PaperMenuSession session) {
        Player player = playerLookup.apply(session.viewerId());
        removePrompt(session.viewerId());
        if (player == null) {
            session.retireScheduling();
            sessions.remove(session.viewerId(), session);
            return;
        }
        if (!ownsPlayerRegion(player)) {
            staleInventoryGuardRequired = true;
            session.retireScheduling();
            sessions.remove(session.viewerId(), session);
            return;
        }
        session.endCustodyTransition();
        if (!session.beginCustodyTransition()) {
            return;
        }
        if (!settleCustody(
                session,
                player,
                false,
                MenuCustodyGesture.SettleReason.SHUTDOWN)) {
            retireCustody(session);
        }
        if (sessions.remove(session.viewerId(), session)) {
            session.detach(player);
            access.closeInventory(player);
        }
        session.endCustodyTransition();
    }

    private static boolean ownsPlayerRegion(Player player) {
        try {
            return Bukkit.isOwnedByCurrentRegion(player);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean handleCustodyClick(
            PaperMenuSession session,
            Player player,
            InventoryClickEvent event,
            Inventory topInventory,
            int rawSlot
    ) {
        ReactiveClickBinding click = toReactiveClick(event.getClick());
        String targetKey = rawSlot >= 0 && rawSlot < topInventory.getSize()
                ? session.state().custodyTargetAt(rawSlot).orElse(null)
                : null;
        boolean viewerSlot = rawSlot >= topInventory.getSize();
        boolean outside = rawSlot < 0;
        if (targetKey == null && !viewerSlot && !outside) {
            return false;
        }
        if (click == null) {
            return true;
        }

        MenuCustodyGesture gesture;
        ItemStack observedItem = null;
        if (outside) {
            gesture = new MenuCustodyGesture.OutsideClick(click.button());
            if (!allowInput(session, new CustodyOutsideInput(click.button()))) {
                return true;
            }
        } else if (viewerSlot) {
            int slot = event.getSlot();
            if (slot < 0 || slot >= storageSize(player.getInventory())) {
                return true;
            }
            observedItem = copyItem(event.getCurrentItem());
            MenuViewerSlot observation = new MenuViewerSlot(
                    session.nextCustodyObservationId(),
                    slot,
                    toMenuStack(observedItem));
            gesture = new MenuCustodyGesture.ViewerClick(observation, click.button(), click.shift());
            if (!allowInput(session, new CustodyViewerInput(slot, click.button(), click.shift()))) {
                return true;
            }
        } else {
            gesture = new MenuCustodyGesture.TargetClick(targetKey, click.button(), click.shift());
            if (!allowInput(session, new ReactiveTopClickInput(rawSlot, click.button(), click.shift()))) {
                return true;
            }
        }

        executeCustodyGesture(session, player, gesture, observedItem, true);
        return true;
    }

    private void handleCustodyDrag(
            PaperMenuSession session,
            Player player,
            InventoryDragEvent event,
            Inventory topInventory
    ) {
        MenuClick button = switch (event.getType()) {
            case EVEN -> MenuClick.LEFT;
            case SINGLE -> MenuClick.RIGHT;
            default -> null;
        };
        if (button == null) {
            return;
        }

        List<String> targetKeys = new ArrayList<>();
        boolean targetsOnly = true;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
                targetsOnly = false;
                continue;
            }
            String targetKey = session.state().custodyTargetAt(rawSlot).orElse(null);
            if (targetKey == null) {
                targetsOnly = false;
            } else if (!targetKeys.contains(targetKey)) {
                targetKeys.add(targetKey);
            }
        }
        if (targetKeys.isEmpty()) {
            return;
        }
        MenuCustodyGesture gesture = new MenuCustodyGesture.TargetDrag(targetKeys, button);
        List<Integer> fingerprintSlots = event.getRawSlots().stream().sorted().toList();
        if (!allowInput(session, new CustodyDragInput(button, fingerprintSlots))) {
            return;
        }
        executeCustodyGesture(session, player, gesture, copyItem(event.getOldCursor()), targetsOnly);
    }

    private void executeCustodyGesture(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture gesture,
            ItemStack observedItem,
            boolean structurallySupported
    ) {
        if (session.custodyTransitioning()) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.TRANSITION_IN_PROGRESS);
            return;
        }
        if (!structurallySupported) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
            return;
        }

        UserCallbackFence callbackFence = captureUserCallbackFence(session);
        MenuCustodyDecision decision;
        try {
            MenuCustodySnapshot snapshot = session.custody().snapshot();
            decision = invokeUserCallback(session, () -> session.state().decideCustody(gesture, snapshot));
        } catch (RuntimeException exception) {
            if (userCallbackFenceHolds(session, player, callbackFence, true, true, true)) {
                rejectCustody(session, player, gesture, MenuCustodyFailure.POLICY_REJECTED);
            }
            return;
        }
        if (!userCallbackFenceHolds(session, player, callbackFence, true, true, true)) {
            return;
        }
        if (!(decision instanceof MenuCustodyDecision.Move move)) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.POLICY_REJECTED);
            return;
        }

        switch (gesture) {
            case MenuCustodyGesture.ViewerClick viewerClick ->
                    executeViewerCustody(session, player, viewerClick, observedItem, move.destination());
            case MenuCustodyGesture.TargetClick targetClick ->
                    executeTargetCustody(session, player, targetClick, move.destination());
            case MenuCustodyGesture.TargetDrag targetDrag ->
                    executeTargetDragCustody(session, player, targetDrag, move.destination());
            case MenuCustodyGesture.OutsideClick outsideClick ->
                    executeOutsideCustody(session, player, outsideClick, move.destination());
            case MenuCustodyGesture.Settle ignored ->
                    rejectCustody(session, player, gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
        }
    }

    private void executeViewerCustody(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture.ViewerClick gesture,
            ItemStack observedItem,
            MenuCustodyDestination destination
    ) {
        int slot = gesture.slot().slot();
        ItemStack liveItem = copyItem(player.getInventory().getItem(slot));
        if (!sameItem(liveItem, observedItem)) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.STALE_INPUT);
            return;
        }

        boolean viewerOccupied = !emptyItem(observedItem);
        boolean cursorOccupied = session.custody().cursor().isPresent();
        if (viewerOccupied) {
            if (cursorOccupied) {
                rejectCustody(session, player, gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
                return;
            }
            MenuCustodyLedger.Destination ledgerDestination = switch (destination) {
                case MenuCustodyDestination.Cursor ignored -> new MenuCustodyLedger.Destination.Cursor();
                case MenuCustodyDestination.Target target -> new MenuCustodyLedger.Destination.Target(target.key());
                default -> null;
            };
            if (ledgerDestination == null) {
                rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
                return;
            }
            acquireCustody(session, player, gesture, observedItem, slot, ledgerDestination);
            return;
        }

        if (!cursorOccupied) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
            return;
        }
        if (!(destination instanceof MenuCustodyDestination.ViewerSlot viewerDestination)
                || viewerDestination.slot().observationId() != gesture.slot().observationId()
                || viewerDestination.slot().slot() != slot) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        transferCustody(
                session,
                player,
                gesture,
                new MenuCustodyLedger.Source.Cursor(),
                new MenuCustodyLedger.Destination.Released(),
                slot);
    }

    private void executeTargetCustody(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture.TargetClick gesture,
            MenuCustodyDestination destination
    ) {
        boolean targetOccupied = session.custody().target(gesture.targetKey()).isPresent();
        boolean cursorOccupied = session.custody().cursor().isPresent();
        if (targetOccupied == cursorOccupied) {
            rejectCustody(session, player, gesture,
                    targetOccupied ? MenuCustodyFailure.OCCUPIED_DESTINATION : MenuCustodyFailure.UNSUPPORTED_GESTURE);
            return;
        }

        if (cursorOccupied) {
            if (!(destination instanceof MenuCustodyDestination.Target target)
                    || !target.key().equals(gesture.targetKey())) {
                rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
                return;
            }
            transferCustody(
                    session,
                    player,
                    gesture,
                    new MenuCustodyLedger.Source.Cursor(),
                    new MenuCustodyLedger.Destination.Target(gesture.targetKey()),
                    null);
            return;
        }

        MenuCustodyLedger.Source source = new MenuCustodyLedger.Source.Target(gesture.targetKey());
        if (destination instanceof MenuCustodyDestination.Cursor) {
            transferCustody(
                    session,
                    player,
                    gesture,
                    source,
                    new MenuCustodyLedger.Destination.Cursor(),
                    null);
            return;
        }
        if (destination instanceof MenuCustodyDestination.Origin) {
            int origin = session.custody().target(gesture.targetKey()).orElseThrow().originalViewerSlot();
            transferCustody(
                    session,
                    player,
                    gesture,
                    source,
                    new MenuCustodyLedger.Destination.Released(),
                    origin);
            return;
        }
        rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
    }

    private void executeTargetDragCustody(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture.TargetDrag gesture,
            MenuCustodyDestination destination
    ) {
        if (gesture.targetKeys().size() != 1
                || session.custody().cursor().isEmpty()
                || !(destination instanceof MenuCustodyDestination.Target target)
                || !target.key().equals(gesture.targetKeys().getFirst())
                || session.custody().target(target.key()).isPresent()) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
            return;
        }
        transferCustody(
                session,
                player,
                gesture,
                new MenuCustodyLedger.Source.Cursor(),
                new MenuCustodyLedger.Destination.Target(target.key()),
                null);
    }

    private void executeOutsideCustody(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture.OutsideClick gesture,
            MenuCustodyDestination destination
    ) {
        if (session.custody().cursor().isEmpty()) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
            return;
        }
        if (!(destination instanceof MenuCustodyDestination.Origin)) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        int origin = session.custody().cursor().orElseThrow().originalViewerSlot();
        transferCustody(
                session,
                player,
                gesture,
                new MenuCustodyLedger.Source.Cursor(),
                new MenuCustodyLedger.Destination.Released(),
                origin);
    }

    private void acquireCustody(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture gesture,
            ItemStack nativeItem,
            int viewerSlot,
            MenuCustodyLedger.Destination destination
    ) {
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = session.custody().prepareAcquire(
                    copyItem(nativeItem),
                    Objects.requireNonNull(toMenuStack(nativeItem)),
                    viewerSlot,
                    destination);
        } catch (IllegalArgumentException exception) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        } catch (IllegalStateException exception) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
            return;
        }

        if (!sameItem(player.getInventory().getItem(viewerSlot), nativeItem)) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.STALE_INPUT);
            return;
        }
        if (destination instanceof MenuCustodyLedger.Destination.Cursor
                && !emptyItem(player.getItemOnCursor())) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
            return;
        }
        if (destination instanceof MenuCustodyLedger.Destination.Target target
                && !targetMatchesBase(session, target.key())) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.STALE_INPUT);
            return;
        }

        NativeMutation mutation = new NativeMutation(player, session.inventory());
        try {
            mutation.setViewerSlot(viewerSlot, null);
            if (destination instanceof MenuCustodyLedger.Destination.Cursor) {
                mutation.setCursor(transfer.entry().nativeItem());
            } else if (destination instanceof MenuCustodyLedger.Destination.Target target) {
                mutation.setTopSlot(session.custody().targetSlots().get(target.key()), transfer.entry().nativeItem());
            }
            session.custody().commit(transfer);
        } catch (RuntimeException exception) {
            if (!mutation.rollback()) {
                quarantineUncertainNativeState(session, player);
                return;
            }
            rejectCustody(session, player, gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            return;
        }
        dispatchCustodyCommitted(session, player, transfer.operationId(), gesture);
    }

    private void transferCustody(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture gesture,
            MenuCustodyLedger.Source source,
            MenuCustodyLedger.Destination destination,
            Integer releaseSlot
    ) {
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = session.custody().prepareTransfer(source, destination);
        } catch (IllegalArgumentException exception) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        } catch (IllegalStateException exception) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
            return;
        }

        ItemStack exactItem = transfer.entry().nativeItem();
        if (source instanceof MenuCustodyLedger.Source.Cursor
                && !sameItem(player.getItemOnCursor(), exactItem)) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.STALE_INPUT);
            return;
        }
        if (source instanceof MenuCustodyLedger.Source.Target target) {
            Integer topSlot = session.custody().targetSlots().get(target.key());
            if (topSlot == null || !sameItem(session.inventory().getItem(topSlot), exactItem)) {
                rejectCustody(session, player, gesture, MenuCustodyFailure.STALE_INPUT);
                return;
            }
        }
        if (destination instanceof MenuCustodyLedger.Destination.Cursor
                && !emptyItem(player.getItemOnCursor())) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
            return;
        }
        if (destination instanceof MenuCustodyLedger.Destination.Target target
                && !targetMatchesBase(session, target.key())) {
            rejectCustody(session, player, gesture, MenuCustodyFailure.STALE_INPUT);
            return;
        }
        if (destination instanceof MenuCustodyLedger.Destination.Released) {
            if (releaseSlot == null || releaseSlot < 0 || releaseSlot >= storageSize(player.getInventory())) {
                rejectCustody(session, player, gesture, MenuCustodyFailure.INVALID_DESTINATION);
                return;
            }
            if (!emptyItem(player.getInventory().getItem(releaseSlot))) {
                rejectCustody(session, player, gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
                return;
            }
        }

        NativeMutation mutation = new NativeMutation(player, session.inventory());
        try {
            if (source instanceof MenuCustodyLedger.Source.Cursor) {
                mutation.setCursor(null);
            } else if (source instanceof MenuCustodyLedger.Source.Target target) {
                int topSlot = session.custody().targetSlots().get(target.key());
                mutation.setTopSlot(topSlot, renderBaseSlot(session, topSlot));
            }

            if (destination instanceof MenuCustodyLedger.Destination.Cursor) {
                mutation.setCursor(exactItem);
            } else if (destination instanceof MenuCustodyLedger.Destination.Target target) {
                mutation.setTopSlot(session.custody().targetSlots().get(target.key()), exactItem);
            } else if (destination instanceof MenuCustodyLedger.Destination.Released) {
                mutation.setViewerSlot(releaseSlot, exactItem);
            }
            session.custody().commit(transfer);
        } catch (RuntimeException exception) {
            if (!mutation.rollback()) {
                quarantineUncertainNativeState(session, player);
                return;
            }
            rejectCustody(session, player, gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            return;
        }
        dispatchCustodyCommitted(session, player, transfer.operationId(), gesture);
    }

    private void dispatchCustodyCommitted(
            PaperMenuSession session,
            Player player,
            long operationId,
            MenuCustodyGesture gesture
    ) {
        dispatchCustodyOutcome(session, player,
                new ReactiveMenuInput.CustodyCommitted(operationId, gesture, session.custody().snapshot()));
    }

    private void rejectCustody(
            PaperMenuSession session,
            Player player,
            MenuCustodyGesture gesture,
            MenuCustodyFailure failure
    ) {
        dispatchCustodyOutcome(session, player,
                new ReactiveMenuInput.CustodyRejected(
                        session.custody().nextOperationId(),
                        gesture,
                        failure,
                        session.custody().snapshot()));
    }

    private void dispatchCustodyOutcome(
            PaperMenuSession session,
            Player player,
            ReactiveMenuInput input
    ) {
        try {
            handleReactiveInput(session, player, input, null);
        } catch (RuntimeException exception) {
            quarantineSession(session, player);
            throw exception;
        }
    }

    private ItemStack renderBaseSlot(PaperMenuSession session, int slot) {
        return renderer.render(session.renderedSlots().get(slot));
    }

    private boolean targetMatchesBase(PaperMenuSession session, String targetKey) {
        Integer slot = session.custody().targetSlots().get(targetKey);
        return slot != null && sameItem(session.inventory().getItem(slot), renderBaseSlot(session, slot));
    }

    boolean prepareInventoryRebuild(PaperMenuSession session, Player player) {
        if (!session.beginCustodyTransition()) {
            return false;
        }
        if (!settleCustody(
                session,
                player,
                false,
                MenuCustodyGesture.SettleReason.NAVIGATE)) {
            session.endCustodyTransition();
            return false;
        }
        return true;
    }

    void openPreparedRefresh(
            PaperMenuSession session,
            Player player,
            PaperMenuSession.PreparedInventory prepared
    ) {
        Runnable action = MenuTrace.propagate(() -> {
            if (sessions.get(session.viewerId()) != session) {
                session.endCustodyTransition();
                return;
            }
            try {
                openReplacementInventory(session, player, prepared.inventory());
                session.commitInitial(prepared);
            } catch (RuntimeException exception) {
                restoreInventoryAfterFailedTransition(session, player);
            } finally {
                session.endCustodyTransition();
            }
        });
        if (session.shouldDeferInventoryTransitions()) {
            session.queueInventoryReplacement();
            try {
                scheduleNextTick(session, action);
            } catch (RuntimeException exception) {
                session.endCustodyTransition();
                throw exception;
            }
            return;
        }
        action.run();
    }

    private boolean drainCustodyForTransition(
            PaperMenuSession session,
            MenuCustodyGesture.SettleReason reason
    ) {
        if (!session.beginCustodyTransition()) {
            return false;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (!settleCustody(session, player, false, reason)) {
            session.endCustodyTransition();
            return false;
        }
        return true;
    }

    private boolean settleCustody(
            PaperMenuSession session,
            Player player,
            boolean leaveCursorForHost,
            MenuCustodyGesture.SettleReason reason
    ) {
        if (session.custody().empty()) {
            return true;
        }
        if (player == null) {
            return false;
        }

        long operationId = 0L;
        if (session.custody().cursor().isPresent()) {
            operationId = releaseCustodyForLifecycle(
                session,
                player,
                new MenuCustodyLedger.Source.Cursor(),
                leaveCursorForHost);
            if (operationId == 0L) {
                return false;
            }
        }
        List<String> targetKeys = new ArrayList<>(session.custody().snapshot().targets().keySet());
        for (String targetKey : targetKeys) {
            operationId = releaseCustodyForLifecycle(
                    session,
                    player,
                    new MenuCustodyLedger.Source.Target(targetKey),
                    false);
            if (operationId == 0L) {
                return false;
            }
        }
        if (!session.custody().empty()) {
            return false;
        }
        return dispatchCustodySettlement(session, player, operationId, reason);
    }

    private boolean settleCustodyToDeathDrops(
            PaperMenuSession session,
            Player player,
            List<ItemStack> drops
    ) {
        if (session.custody().empty()) {
            return true;
        }
        long operationId = 0L;
        if (session.custody().cursor().isPresent()) {
            operationId = releaseCustodyToDeathDrops(
                    session,
                    player,
                    new MenuCustodyLedger.Source.Cursor(),
                    drops);
            if (operationId == 0L) {
                return false;
            }
        }
        for (String targetKey : List.copyOf(session.custody().snapshot().targets().keySet())) {
            operationId = releaseCustodyToDeathDrops(
                    session,
                    player,
                    new MenuCustodyLedger.Source.Target(targetKey),
                    drops);
            if (operationId == 0L) {
                return false;
            }
        }
        if (!session.custody().empty()) {
            return false;
        }
        return dispatchCustodySettlement(
                session,
                player,
                operationId,
                MenuCustodyGesture.SettleReason.DEATH);
    }

    private boolean dispatchCustodySettlement(
            PaperMenuSession session,
            Player player,
            long operationId,
            MenuCustodyGesture.SettleReason reason
    ) {
        MenuCustodyGesture gesture = new MenuCustodyGesture.Settle(reason);
        try {
            UserCallbackFence callbackFence = captureUserCallbackFence(session);
            invokeUserCallback(session, () -> session.state().prepareReactive(
                    new ReactiveMenuInput.CustodyCommitted(
                            operationId,
                            gesture,
                            session.custody().snapshot())).commit());
            boolean current = userCallbackFenceHolds(session, player, callbackFence, false, false, false);
            if (current) {
                session.markSettledCustodyViewDirty();
            }
            return current;
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
            return false;
        }
    }

    private long releaseCustodyToDeathDrops(
            PaperMenuSession session,
            Player player,
            MenuCustodyLedger.Source source,
            List<ItemStack> drops
    ) {
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = session.custody().prepareTransfer(
                    source,
                    new MenuCustodyLedger.Destination.Released());
        } catch (RuntimeException exception) {
            return 0L;
        }
        ItemStack exactItem = transfer.entry().nativeItem();
        try {
            if (source instanceof MenuCustodyLedger.Source.Cursor
                    && !sameItem(player.getItemOnCursor(), exactItem)) {
                session.custody().commit(transfer);
                return transfer.operationId();
            }
            if (source instanceof MenuCustodyLedger.Source.Target target) {
                int topSlot = session.custody().targetSlots().get(target.key());
                if (!sameItem(session.inventory().getItem(topSlot), exactItem)) {
                    session.custody().commit(transfer);
                    return transfer.operationId();
                }
            }
        } catch (RuntimeException exception) {
            return 0L;
        }

        NativeMutation mutation = new NativeMutation(player, session.inventory());
        try {
            if (source instanceof MenuCustodyLedger.Source.Cursor) {
                mutation.setCursor(null);
            } else if (source instanceof MenuCustodyLedger.Source.Target target) {
                int topSlot = session.custody().targetSlots().get(target.key());
                mutation.setTopSlot(topSlot, renderBaseSlot(session, topSlot));
            }
            session.custody().commit(transfer);
        } catch (RuntimeException exception) {
            if (!mutation.rollback()) {
                quarantineUncertainNativeState(session, player);
            }
            return 0L;
        }

        try {
            drops.add(copyItem(exactItem));
        } catch (RuntimeException ignored) {
        }
        return transfer.operationId();
    }

    private void quarantineSession(PaperMenuSession session, Player player) {
        session.endCustodyTransition();
        session.beginCustodyTransition();
        if (player == null || !drainCustodyWithoutReducer(session, player, false)) {
            retireCustody(session);
        }
        removePrompt(session.viewerId());
        if (!sessions.remove(session.viewerId(), session)) {
            session.endCustodyTransition();
            return;
        }
        session.detach(player);
        session.endCustodyTransition();
        if (player == null) {
            return;
        }
        Runnable closeAction = () -> {
            if (access.topInventory(player) == session.inventory()) {
                access.closeInventory(player);
            }
        };
        if (session.shouldDeferInventoryTransitions()) {
            scheduleNextTick(session, closeAction);
        } else {
            closeAction.run();
        }
    }

    private void quarantineAfterFailure(PaperMenuSession session, Player player) {
        try {
            quarantineSession(session, player);
        } catch (RuntimeException ignored) {
            removePrompt(session.viewerId());
            sessions.remove(session.viewerId(), session);
            session.endCustodyTransition();
            try {
                session.detach(player);
            } catch (RuntimeException ignoredDetachFailure) {
            }
        }
    }

    private void quarantineUncertainNativeState(PaperMenuSession session, Player player) {
        session.endCustodyTransition();
        session.beginCustodyTransition();
        retireCustody(session);
        removePrompt(session.viewerId());
        sessions.remove(session.viewerId(), session);
        try {
            session.detach(player);
        } catch (RuntimeException ignored) {
        }
        session.endCustodyTransition();
        if (player == null) {
            return;
        }
        Runnable closeAction = () -> {
            if (access.topInventory(player) == session.inventory()) {
                access.closeInventory(player);
            }
        };
        if (session.shouldDeferInventoryTransitions()) {
            scheduleNextTick(session, closeAction);
        } else {
            closeAction.run();
        }
    }

    private boolean drainCustodyWithoutReducer(
            PaperMenuSession session,
            Player player,
            boolean leaveCursorForHost
    ) {
        if (session.custody().cursor().isPresent()
                && releaseCustodyForLifecycle(
                session,
                player,
                new MenuCustodyLedger.Source.Cursor(),
                leaveCursorForHost) == 0L) {
            return false;
        }
        for (String key : List.copyOf(session.custody().snapshot().targets().keySet())) {
            if (releaseCustodyForLifecycle(
                    session,
                    player,
                    new MenuCustodyLedger.Source.Target(key),
                    false) == 0L) {
                return false;
            }
        }
        return session.custody().empty();
    }

    private long releaseCustodyForLifecycle(
            PaperMenuSession session,
            Player player,
            MenuCustodyLedger.Source source,
            boolean leaveCursorForHost
    ) {
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = session.custody().prepareTransfer(
                    source,
                    new MenuCustodyLedger.Destination.Released());
        } catch (RuntimeException exception) {
            return 0L;
        }

        ItemStack exactItem = transfer.entry().nativeItem();
        if (source instanceof MenuCustodyLedger.Source.Cursor
                && !sameItem(player.getItemOnCursor(), exactItem)) {
            session.custody().commit(transfer);
            return transfer.operationId();
        }
        if (source instanceof MenuCustodyLedger.Source.Target target) {
            int topSlot = session.custody().targetSlots().get(target.key());
            if (!sameItem(session.inventory().getItem(topSlot), exactItem)) {
                session.custody().commit(transfer);
                return transfer.operationId();
            }
        }
        if (leaveCursorForHost && source instanceof MenuCustodyLedger.Source.Cursor) {
            session.custody().commit(transfer);
            return transfer.operationId();
        }

        Integer viewerSlot = safeViewerSlot(player.getInventory(), transfer.entry().originalViewerSlot());
        NativeMutation mutation = new NativeMutation(player, session.inventory());
        boolean dropAfterCommit = false;
        try {
            if (source instanceof MenuCustodyLedger.Source.Cursor) {
                mutation.setCursor(null);
            } else if (source instanceof MenuCustodyLedger.Source.Target target) {
                int topSlot = session.custody().targetSlots().get(target.key());
                mutation.setTopSlot(topSlot, renderBaseSlot(session, topSlot));
            }
            if (viewerSlot != null) {
                mutation.setViewerSlot(viewerSlot, exactItem);
            } else if (emptyItem(player.getItemOnCursor())) {
                mutation.setCursor(exactItem);
            } else {
                dropAfterCommit = true;
            }
            session.custody().commit(transfer);
        } catch (RuntimeException exception) {
            if (!mutation.rollback()) {
                quarantineUncertainNativeState(session, player);
            }
            return 0L;
        }
        if (dropAfterCommit) {
            try {
                dropExactItem(player, exactItem);
            } catch (RuntimeException ignored) {
            }
        }
        return transfer.operationId();
    }

    private void retireCustody(PaperMenuSession session) {
        if (session.custody().cursor().isPresent()) {
            retireCustodySource(session, new MenuCustodyLedger.Source.Cursor());
        }
        for (String key : List.copyOf(session.custody().snapshot().targets().keySet())) {
            retireCustodySource(session, new MenuCustodyLedger.Source.Target(key));
        }
    }

    private static void retireCustodySource(PaperMenuSession session, MenuCustodyLedger.Source source) {
        try {
            MenuCustodyLedger.PreparedTransfer<ItemStack> transfer = session.custody().prepareTransfer(
                    source,
                    new MenuCustodyLedger.Destination.Released());
            session.custody().commit(transfer);
        } catch (RuntimeException ignored) {
        }
    }

    private static Integer safeViewerSlot(PlayerInventory inventory, int preferredSlot) {
        int storageSize = storageSize(inventory);
        if (preferredSlot >= 0 && preferredSlot < storageSize
                && emptyItem(inventory.getItem(preferredSlot))) {
            return preferredSlot;
        }
        for (int slot = 0; slot < storageSize; slot++) {
            if (emptyItem(inventory.getItem(slot))) {
                return slot;
            }
        }
        return null;
    }

    private static int storageSize(PlayerInventory inventory) {
        return inventory.getStorageContents().length;
    }

    private static boolean dropExactItem(Player player, ItemStack item) {
        Location location = player.getLocation();
        if (location == null || location.getWorld() == null) {
            return false;
        }
        location.getWorld().dropItemNaturally(location, copyItem(item));
        return true;
    }

    private void handleReactiveTopClick(PaperMenuSession session, Player player, int rawSlot, InventoryClickEvent event) {
        ReactiveClickBinding click = toReactiveClick(event.getClick());
        if (click == null) {
            return;
        }
        MenuTrace.field("button", click.button());
        MenuInteraction interaction = session.state().interaction(rawSlot, click.button()).orElse(null);
        ReactiveTopClickInput fingerprint = new ReactiveTopClickInput(rawSlot, click.button(), click.shift());
        if (interaction != null && !(interaction.action() instanceof MenuSlotAction.Dispatch)) {
            if (!allowInput(session, fingerprint)) {
                return;
            }
            handleDirectInteraction(session, player, click.button(), interaction);
            return;
        }
        if (interaction == null && !session.state().acceptsReactiveClick(rawSlot)) {
            return;
        }
        if (!allowInput(session, fingerprint)) {
            return;
        }
        Object message = interaction != null ? ((MenuSlotAction.Dispatch) interaction.action()).message() : null;
        handleReactiveInput(session, player,
                new ReactiveMenuInput.Click(rawSlot, click.button(), click.shift(), message),
                interaction);
    }

    private void handleReactiveInput(PaperMenuSession session, Player player, ReactiveMenuInput input, MenuInteraction interaction) {
        try {
            if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
                playInteractionSound(player, interaction);
            }
            UserCallbackFence callbackFence = captureUserCallbackFence(session);
            long revision = session.state().revision();
            List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.reactiveDispatch",
                    () -> invokeUserCallback(session, () -> session.state().dispatchReactive(input)));
            boolean requireActiveTop = input instanceof ReactiveMenuInput.Click
                    || input instanceof ReactiveMenuInput.CustodyCommitted
                    || input instanceof ReactiveMenuInput.CustodyRejected;
            if (!userCallbackFenceHolds(session, player, callbackFence, requireActiveTop, true, false)) {
                return;
            }
            boolean handled = MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects));
            if (!handled && session.state().revision() != revision) {
                MenuTrace.time("session.refresh", () -> session.refresh(player));
            }
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
        }
    }

    private void handleDirectInteraction(PaperMenuSession session, Player player, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(player, interaction);
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> {
                if (!drainCustodyForTransition(session, MenuCustodyGesture.SettleReason.NAVIGATE)) {
                    return;
                }
                try {
                    session.state().prepareOpenFrame(openFrame.frameId()).ifPresentOrElse(
                            transition -> applyPreparedTransition(session, transition),
                            () -> restoreSettledCustodyView(session));
                } catch (RuntimeException exception) {
                    restoreSettledCustodyView(session);
                }
            }
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                long before = session.actionVersion();
                MenuContext context = new MenuContext(click, session.state().frameId(), session.state().values(), session);
                UserCallbackFence callbackFence = captureUserCallbackFence(session);
                try {
                    invokeUserCallback(session, () -> {
                        execute.action().execute(context);
                        return null;
                    });
                } catch (RuntimeException exception) {
                    quarantineAfterFailure(session, player);
                    return;
                }
                if (session.actionVersion() == before
                        && userCallbackFenceHolds(session, player, callbackFence, true, true, true)) {
                    MenuTrace.time("session.refresh", () -> session.refresh(player));
                }
            }
            case MenuSlotAction.Dispatch ignored -> {
            }
        }
    }

    private boolean applyEffects(PaperMenuSession session, Player player, List<ReactiveMenuEffect> effects) {
        for (ReactiveMenuEffect effect : effects) {
            switch (effect) {
                case ReactiveMenuEffect.RequestTextPrompt prompt -> {
                    openTextPrompt(session, player, prompt.request());
                    return true;
                }
                case ReactiveMenuEffect.Open open -> {
                    replace(session, open.menu());
                    return true;
                }
                case ReactiveMenuEffect.Replace replace -> {
                    replaceCurrent(session, replace.menu());
                    return true;
                }
                case ReactiveMenuEffect.SetViewerInventorySlot ignored -> throw new IllegalStateException();
                case ReactiveMenuEffect.Close ignored -> {
                    close(session);
                    return true;
                }
            }
        }
        return false;
    }

    private void playInteractionSound(Player player, MenuInteraction interaction) {
        if (interaction == null) {
            return;
        }
        Key soundCueKey = interaction.soundCueKey();
        if (soundCueKey != null) {
            sounds.play(player, soundCueKey);
        }
    }

    void openInventory(PaperMenuSession session, Player player, Inventory inventory) {
        if (promptBlocksInventoryOpen(session)) {
            return;
        }
        if (!session.shouldDeferInventoryTransitions()) {
            if (closed
                    || sessions.get(session.viewerId()) != session
                    || session.inventory() != inventory) {
                return;
            }
            MenuTrace.time("runtime.inventoryOpen", () -> access.openInventory(player, inventory));
            return;
        }
        scheduleNextTick(session, MenuTrace.propagate(() -> {
            if (!closed
                    && sessions.get(session.viewerId()) == session
                    && session.inventory() == inventory
                    && !promptBlocksInventoryOpen(session)) {
                MenuTrace.time("runtime.inventoryOpen", () -> access.openInventory(player, inventory));
            }
        }));
    }

    private boolean promptBlocksInventoryOpen(PaperMenuSession session) {
        PendingTextPrompt prompt = prompts.get(session.viewerId());
        return prompt != null && prompt.session() == session;
    }

    private PendingTextPrompt removePrompt(UUID viewerId) {
        PendingTextPrompt prompt = prompts.remove(viewerId);
        if (prompt != null) {
            prompt.retire();
        }
        return prompt;
    }

    private boolean removePrompt(UUID viewerId, PendingTextPrompt prompt) {
        if (!prompts.remove(viewerId, prompt)) {
            return false;
        }
        prompt.retire();
        return true;
    }

    private PaperMenuSession session(Inventory inventory) {
        PaperMenuSession session = ownedSession(inventory);
        if (session == null) {
            return null;
        }
        return session.inventory() == inventory ? session : null;
    }

    private static PaperMenuSession ownedSession(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder(false) instanceof PaperMenuSession session)) {
            return null;
        }
        return session;
    }

    private void openTextPrompt(PaperMenuSession session, Player player, ReactiveTextPromptRequest request) {
        if (!drainCustodyForTransition(session, MenuCustodyGesture.SettleReason.PROMPT)) {
            return;
        }
        PendingTextPrompt prompt = null;
        try {
            ReactiveTextPromptMode resolvedMode = resolvePromptMode(request);
            Location signLocation = player.getLocation().toBlockLocation();
            prompt = new PendingTextPrompt(
                    session,
                    request,
                    resolvedMode,
                    signLocation,
                    PendingTextPromptPhase.AWAITING_MENU_CLOSE);
            prompts.put(player.getUniqueId(), prompt);
            armPromptTimeout(prompt, player);
            if (closeViewerInventoryForPrompt(session, player)) {
                return;
            }
            activatePrompt(prompt, player);
        } catch (RuntimeException exception) {
            if (prompt != null) {
                abortPrompt(prompt, player);
            } else {
                session.endCustodyTransition();
            }
            throw exception;
        }
    }

    private boolean advancePromptAfterClose(UUID viewerId, PaperMenuSession session, Inventory inventory) {
        PendingTextPrompt prompt = prompts.get(viewerId);
        if (prompt == null || prompt.session() != session
                || prompt.phase() != PendingTextPromptPhase.AWAITING_MENU_CLOSE
                || session.inventory() != inventory) {
            return false;
        }
        prompt.phase(PendingTextPromptPhase.ACTIVE);
        Player player = playerLookup.apply(viewerId);
        if (player != null) {
            scheduleNextTick(session, MenuTrace.propagate(() -> {
                if (prompts.get(viewerId) == prompt && sessions.get(viewerId) == session) {
                    activatePromptSafely(prompt, player);
                }
            }));
        }
        return true;
    }

    private void completePrompt(PendingTextPrompt prompt, ReactiveMenuInput input) {
        if (!removePrompt(prompt.session().viewerId(), prompt)) {
            return;
        }
        PaperMenuSession session = prompt.session();
        Player player = playerLookup.apply(session.viewerId());
        if (player == null || sessions.get(session.viewerId()) != session) {
            session.endCustodyTransition();
            return;
        }
        try {
            restorePromptClientBlock(prompt, player);
        } finally {
            session.endCustodyTransition();
        }
        UserCallbackFence callbackFence = captureUserCallbackFence(session);
        List<ReactiveMenuEffect> effects;
        try {
            effects = MenuTrace.time("runtime.reactiveDispatch",
                    () -> invokeUserCallback(session, () -> session.state().dispatchReactive(input)));
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
            return;
        }
        if (!userCallbackFenceHolds(session, player, callbackFence, false, true, false)) {
            return;
        }
        try {
            scheduleNextTick(session, MenuTrace.propagate(() -> {
                if (sessions.get(session.viewerId()) != session) {
                    return;
                }
                try {
                    if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects))) {
                        MenuTrace.time("session.refresh", () -> session.refresh(player));
                    }
                } catch (RuntimeException exception) {
                    quarantineAfterFailure(session, player);
                }
            }));
        } catch (RuntimeException exception) {
            quarantineAfterFailure(session, player);
        }
    }

    private void activatePrompt(PendingTextPrompt prompt, Player player) {
        prompt.phase(PendingTextPromptPhase.ACTIVE);
        switch (prompt.mode()) {
            case SIGN -> {
                PreparedVirtualSign virtualSign = virtualSigns.prepare(prompt.request(), player.getUniqueId());
                player.sendBlockChange(prompt.signLocation(), virtualSign.blockData());
                player.sendBlockUpdate(prompt.signLocation(), virtualSign.tileState());
                player.openVirtualSign(Position.block(prompt.signLocation()), Side.FRONT);
            }
            case CHAT -> player.sendMessage(Component.text(
                    prompt.request().prompt() + " Type your response in chat or send 'cancel' to keep the current value."));
            case PROMPT -> dialogPrompts.open(
                    player,
                    prompt.request(),
                    value -> completePrompt(prompt, new ReactiveMenuInput.TextPromptSubmitted(
                            prompt.request().key(),
                            value,
                            ReactiveTextPromptMode.PROMPT)),
                    () -> completePrompt(prompt, new ReactiveMenuInput.TextPromptCancelled(
                            prompt.request().key(),
                            ReactiveTextPromptMode.PROMPT)));
            default -> throw new IllegalStateException("Unsupported prompt mode: " + prompt.mode());
        }
    }

    private void armPromptTimeout(PendingTextPrompt prompt, Player player) {
        MenuTickHandle timeout = taskScheduler.after(
                player,
                TEXT_PROMPT_TIMEOUT_TICKS,
                MenuTrace.propagate(() -> completePrompt(
                        prompt,
                        new ReactiveMenuInput.TextPromptCancelled(
                                prompt.request().key(),
                                prompt.mode()))));
        prompt.armTimeout(timeout);
    }

    private void activatePromptSafely(PendingTextPrompt prompt, Player player) {
        try {
            activatePrompt(prompt, player);
        } catch (RuntimeException exception) {
            abortPrompt(prompt, player);
        }
    }

    private void abortPrompt(PendingTextPrompt prompt, Player player) {
        if (!removePrompt(prompt.session().viewerId(), prompt)) {
            return;
        }
        PaperMenuSession session = prompt.session();
        try {
            restorePromptClientBlock(prompt, player);
        } finally {
            session.endCustodyTransition();
        }
        if (sessions.get(session.viewerId()) == session) {
            try {
                session.refresh(player);
            } catch (RuntimeException exception) {
                quarantineAfterFailure(session, player);
            }
        }
    }

    private void restorePromptClientBlock(PendingTextPrompt prompt, Player player) {
        if (prompt.mode() != ReactiveTextPromptMode.SIGN) {
            return;
        }
        Location signLocation = prompt.signLocation().clone();
        if (signLocation.getWorld() == null) {
            return;
        }
        try {
            taskScheduler.at(signLocation, MenuTrace.propagate(() -> capturePromptBlock(prompt, signLocation, player)));
        } catch (RuntimeException ignored) {
        }
    }

    private void capturePromptBlock(PendingTextPrompt prompt, Location signLocation, Player player) {
        PromptBlockSnapshot snapshot;
        try {
            var block = signLocation.getBlock();
            BlockData blockData = block.getBlockData().clone();
            TileState tileState = block.getState() instanceof TileState state ? state : null;
            snapshot = new PromptBlockSnapshot(signLocation.clone(), blockData, tileState);
        } catch (RuntimeException ignored) {
            return;
        }
        try {
            taskScheduler.next(player, MenuTrace.propagate(() -> sendPromptBlockSnapshot(prompt, player, snapshot)));
        } catch (RuntimeException ignored) {
        }
    }

    private void sendPromptBlockSnapshot(PendingTextPrompt prompt, Player player, PromptBlockSnapshot snapshot) {
        PendingTextPrompt current = prompts.get(prompt.session().viewerId());
        if (current != null
                && current != prompt
                && current.mode() == ReactiveTextPromptMode.SIGN
                && sameBlock(current.signLocation(), snapshot.location())) {
            return;
        }
        try {
            player.sendBlockChange(snapshot.location(), snapshot.blockData());
            if (snapshot.tileState() != null) {
                player.sendBlockUpdate(snapshot.location(), snapshot.tileState());
            }
        } catch (RuntimeException ignored) {
        }
    }

    private boolean closeViewerInventoryForPrompt(PaperMenuSession session, Player player) {
        Inventory activeInventory = session.inventory();
        if (activeInventory == null || access.topInventory(player) != activeInventory) {
            return false;
        }
        if (session.shouldDeferInventoryTransitions()) {
            scheduleNextTick(session, MenuTrace.propagate(() -> {
                try {
                    if (sessions.get(session.viewerId()) == session && access.topInventory(player) == activeInventory) {
                        MenuTrace.time("runtime.inventoryClose", () -> access.closeInventory(player));
                    }
                } catch (RuntimeException exception) {
                    PendingTextPrompt prompt = prompts.get(session.viewerId());
                    if (prompt != null && prompt.session() == session) {
                        abortPrompt(prompt, player);
                    }
                }
            }));
            return true;
        }
        MenuTrace.time("runtime.inventoryClose", () -> access.closeInventory(player));
        return true;
    }

    private static List<Component> paddedSignLines(ReactiveTextPromptRequest request) {
        List<String> source = request.signLines().isEmpty()
                ? List.of(request.initialValue(), "^^^^^^", request.prompt(), "")
                : request.signLines();
        List<Component> lines = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            lines.add(Component.text(index < source.size() ? source.get(index) : ""));
        }
        return List.copyOf(lines);
    }

    private static ReactiveTextPromptMode resolvePromptMode(ReactiveTextPromptRequest request) {
        return switch (request.preferredMode()) {
            case PROMPT -> ReactiveTextPromptMode.PROMPT;
            case SIGN -> ReactiveTextPromptMode.SIGN;
            default -> ReactiveTextPromptMode.CHAT;
        };
    }

    private boolean allowInput(PaperMenuSession session, AcceptedInput input) {
        PaperMenuSession.InputGateResult result = session.acceptInput(input);
        if (result == PaperMenuSession.InputGateResult.ACCEPTED) {
            return true;
        }
        MenuTrace.field("inputGuard", result == PaperMenuSession.InputGateResult.DUPLICATE ? "duplicate" : "tickCap");
        MenuTrace.field("guardInputKind", input.kind());
        MenuTrace.incrementCount("suppressedInputs");
        MenuTrace.incrementCount(result == PaperMenuSession.InputGateResult.DUPLICATE
                ? "suppressedInputDuplicates"
                : "suppressedInputTickCap");
        return false;
    }

    private static MenuClick toCompiledMenuClick(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> MenuClick.LEFT;
            case RIGHT -> MenuClick.RIGHT;
            default -> null;
        };
    }

    private static ReactiveClickBinding toReactiveClick(ClickType clickType) {
        return switch (clickType) {
            case LEFT, WINDOW_BORDER_LEFT -> new ReactiveClickBinding(MenuClick.LEFT, false);
            case SHIFT_LEFT -> new ReactiveClickBinding(MenuClick.LEFT, true);
            case RIGHT, WINDOW_BORDER_RIGHT -> new ReactiveClickBinding(MenuClick.RIGHT, false);
            case SHIFT_RIGHT -> new ReactiveClickBinding(MenuClick.RIGHT, true);
            default -> null;
        };
    }

    private static MenuStack toMenuStack(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        Material type = itemStack.getType();
        if (type == null || type == Material.AIR) {
            return null;
        }
        MenuStack.Builder builder = MenuStack.builder(PaperMenuIcons.fromItemStack(itemStack))
                .amount(Math.max(1, itemStack.getAmount()));
        ItemMeta meta = itemStack.getItemMeta();
        Component name = meta != null ? meta.displayName() : null;
        if (name != null) {
            builder.name(name);
        } else {
            builder.name(fallbackName(type));
        }
        return builder.build();
    }

    private static String fallbackName(Material material) {
        String[] parts = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private void trace(Player player, String cause, Runnable action) {
        MenuTrace.withTrace(traceController, traceSink, "paper", player.getUniqueId(), cause, action);
    }

    private static String flatten(Component component) {
        return PLAIN_TEXT.serialize(component);
    }

    private static boolean sameBlock(Location left, Location right) {
        return left != null && right != null
                && Objects.equals(left.getWorld(), right.getWorld())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private static boolean sameBlock(Location left, Position right) {
        return left != null && right != null
                && left.getBlockX() == right.blockX()
                && left.getBlockY() == right.blockY()
                && left.getBlockZ() == right.blockZ();
    }

    private void inInventoryInteraction(Inventory inventory, Runnable action) {
        PaperMenuSession session = ownedSession(inventory);
        if (session == null) {
            action.run();
            return;
        }
        session.inInventoryInteraction(action);
    }

    private void closeStaleInventoryOnNextTick(PaperMenuSession session, HumanEntity viewer) {
        if (session == null || !(viewer instanceof Player player)) {
            return;
        }
        Inventory staleInventory = session.inventory();
        try {
            scheduleNextTick(session, () -> {
                if (access.topInventory(player) == staleInventory) {
                    access.closeInventory(player);
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private record ReactiveClickBinding(MenuClick button, boolean shift) {
    }

    private record PromptBlockSnapshot(Location location, BlockData blockData, TileState tileState) {
    }

    interface PaperVirtualSignSupport {

        PreparedVirtualSign prepare(ReactiveTextPromptRequest request, UUID allowedEditorId);

        static PaperVirtualSignSupport live() {
            return LivePaperVirtualSignSupport.INSTANCE;
        }
    }

    interface PaperDialogPromptSupport {

        void open(Player player, ReactiveTextPromptRequest request, Consumer<String> submit, Runnable cancel);

        static PaperDialogPromptSupport live() {
            return LivePaperDialogPromptSupport.INSTANCE;
        }
    }

    record PreparedVirtualSign(BlockData blockData, TileState tileState) {

        PreparedVirtualSign {
            Objects.requireNonNull(blockData, "blockData");
            Objects.requireNonNull(tileState, "tileState");
        }
    }

    private enum LivePaperVirtualSignSupport implements PaperVirtualSignSupport {
        INSTANCE;

        @Override
        public PreparedVirtualSign prepare(ReactiveTextPromptRequest request, UUID allowedEditorId) {
            BlockData blockData = Bukkit.createBlockData(Material.OAK_SIGN);
            if (!(blockData.createBlockState() instanceof Sign sign)) {
                throw new IllegalStateException("OAK_SIGN did not create a Sign block state");
            }
            List<Component> lines = paddedSignLines(request);
            for (int index = 0; index < lines.size(); index++) {
                sign.getSide(Side.FRONT).line(index, lines.get(index));
            }
            sign.setEditable(true);
            sign.setWaxed(false);
            sign.setAllowedEditorUniqueId(allowedEditorId);
            return new PreparedVirtualSign(blockData, sign);
        }
    }

    private enum LivePaperDialogPromptSupport implements PaperDialogPromptSupport {
        INSTANCE;

        private static final String INPUT_KEY = "value";
        private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(5))
                .build();

        @Override
        public void open(Player player, ReactiveTextPromptRequest request, Consumer<String> submit, Runnable cancel) {
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(Component.text("Menu Prompt"))
                            .canCloseWithEscape(false)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(DialogBody.plainMessage(Component.text(request.prompt()))))
                            .inputs(List.of(DialogInput.text(INPUT_KEY, Component.text("Response"))
                                    .labelVisible(false)
                                    .initial(request.initialValue())
                                    .maxLength(256)
                                    .build()))
                            .build())
                    .type(DialogType.multiAction(List.of(ActionButton.builder(Component.text("Submit"))
                                    .action(DialogAction.customClick(
                                            (response, audience) -> submit.accept(Objects.requireNonNullElse(response.getText(INPUT_KEY), "")),
                                            CALLBACK_OPTIONS))
                                    .build()))
                            .exitAction(ActionButton.builder(Component.text("Cancel"))
                                    .action(DialogAction.customClick((response, audience) -> cancel.run(), CALLBACK_OPTIONS))
                                    .build())
                            .columns(1)
                            .build()));
            player.showDialog(dialog);
        }
    }

    private static ItemStack copyItem(ItemStack item) {
        return emptyItem(item) ? null : item.clone();
    }

    private static boolean emptyItem(ItemStack item) {
        return item == null || item.getType() == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        if (emptyItem(left) || emptyItem(right)) {
            return emptyItem(left) && emptyItem(right);
        }
        return left.equals(right);
    }

    private static final class NativeMutation {

        private final Player player;
        private final Inventory topInventory;
        private final List<Runnable> undoLog = new ArrayList<>();
        private final Set<Integer> capturedViewerSlots = new HashSet<>();
        private final Set<Integer> capturedTopSlots = new HashSet<>();
        private boolean capturedCursor;

        private NativeMutation(Player player, Inventory topInventory) {
            this.player = Objects.requireNonNull(player);
            this.topInventory = Objects.requireNonNull(topInventory);
        }

        private void setViewerSlot(int slot, ItemStack item) {
            if (capturedViewerSlots.add(slot)) {
                ItemStack before = copyItem(player.getInventory().getItem(slot));
                undoLog.add(() -> player.getInventory().setItem(slot, copyItem(before)));
            }
            player.getInventory().setItem(slot, copyItem(item));
        }

        private void setTopSlot(int slot, ItemStack item) {
            if (capturedTopSlots.add(slot)) {
                ItemStack before = copyItem(topInventory.getItem(slot));
                undoLog.add(() -> topInventory.setItem(slot, copyItem(before)));
            }
            topInventory.setItem(slot, copyItem(item));
        }

        private void setCursor(ItemStack item) {
            if (!capturedCursor) {
                ItemStack before = copyItem(player.getItemOnCursor());
                undoLog.add(() -> player.setItemOnCursor(copyItem(before)));
                capturedCursor = true;
            }
            player.setItemOnCursor(copyItem(item));
        }

        private boolean rollback() {
            for (int index = undoLog.size() - 1; index >= 0; index--) {
                try {
                    undoLog.get(index).run();
                } catch (RuntimeException ignored) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class PendingTextPrompt {

        private final PaperMenuSession session;
        private final ReactiveTextPromptRequest request;
        private final ReactiveTextPromptMode mode;
        private final Location signLocation;
        private volatile PendingTextPromptPhase phase;
        private MenuTickHandle timeoutHandle = MenuTickHandle.noop();
        private boolean retired;

        private PendingTextPrompt(
                PaperMenuSession session,
                ReactiveTextPromptRequest request,
                ReactiveTextPromptMode mode,
                Location signLocation,
                PendingTextPromptPhase phase
        ) {
            this.session = session;
            this.request = request;
            this.mode = mode;
            this.signLocation = signLocation;
            this.phase = phase;
        }

        private PaperMenuSession session() {
            return session;
        }

        private ReactiveTextPromptRequest request() {
            return request;
        }

        private ReactiveTextPromptMode mode() {
            return mode;
        }

        private Location signLocation() {
            return signLocation;
        }

        private PendingTextPromptPhase phase() {
            return phase;
        }

        private void phase(PendingTextPromptPhase phase) {
            this.phase = Objects.requireNonNull(phase, "phase");
        }

        private void armTimeout(MenuTickHandle timeoutHandle) {
            Objects.requireNonNull(timeoutHandle, "timeoutHandle");
            boolean cancel;
            synchronized (this) {
                cancel = retired;
                if (!cancel) {
                    this.timeoutHandle = timeoutHandle;
                }
            }
            if (cancel) {
                cancelSafely(timeoutHandle);
            }
        }

        private void retire() {
            MenuTickHandle timeout;
            synchronized (this) {
                if (retired) {
                    return;
                }
                retired = true;
                timeout = timeoutHandle;
                timeoutHandle = MenuTickHandle.noop();
            }
            cancelSafely(timeout);
        }

        private static void cancelSafely(MenuTickHandle handle) {
            try {
                handle.cancel();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private enum PendingTextPromptPhase {
        AWAITING_MENU_CLOSE,
        ACTIVE
    }

    private sealed interface AcceptedInput permits CompiledClickInput, CustodyDragInput,
            CustodyOutsideInput, CustodyViewerInput, ReactiveTopClickInput {

        String kind();
    }

    private record CompiledClickInput(int slot, MenuClick button) implements AcceptedInput {

        @Override
        public String kind() {
            return "compiled-click";
        }
    }

    private record ReactiveTopClickInput(int slot, MenuClick button, boolean shift) implements AcceptedInput {

        @Override
        public String kind() {
            return "reactive-top-click";
        }
    }

    private record CustodyViewerInput(int slot, MenuClick button, boolean shift) implements AcceptedInput {

        @Override
        public String kind() {
            return "reactive-inventory-click";
        }
    }

    private record CustodyDragInput(MenuClick button, List<Integer> slots) implements AcceptedInput {

        @Override
        public String kind() {
            return "reactive-drag";
        }
    }

    private record CustodyOutsideInput(MenuClick button) implements AcceptedInput {

        @Override
        public String kind() {
            return "reactive-drop-cursor";
        }
    }
}
