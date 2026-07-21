package sh.harold.library.menu.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyDestination;
import sh.harold.library.menu.MenuCustodyFailure;
import sh.harold.library.menu.MenuCustodyGesture;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

final class MinestomMenuRuntime implements AutoCloseable {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final int VIEWER_STORAGE_SIZE = PlayerInventory.INNER_INVENTORY_SIZE;
    private static final int MAX_RETIRED_INVENTORIES_PER_VIEWER = 4;

    private final Map<UUID, MinestomMenuSession> sessions = new ConcurrentHashMap<>();
    private final MinestomMenuRenderer renderer;
    private final SoundCueService sounds;
    private final MenuTickScheduler tickScheduler;
    private final Function<Runnable, MenuTickHandle> nextTickScheduler;
    private final MenuTraceController traceController;
    private final Consumer<String> traceSink;
    private final Map<UUID, PendingTextPrompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<Inventory>> retiredInventories = new ConcurrentHashMap<>();
    private final ThreadLocal<ArrayDeque<MinestomMenuSession>> userCallbackSessions =
            ThreadLocal.withInitial(ArrayDeque::new);

    MinestomMenuRuntime(MinestomMenuRenderer renderer, SoundCueService sounds) {
        this(renderer, sounds, MenuTickScheduler.unsupported(), MinestomMenuRuntime::scheduleOnServerTick,
                new MenuTraceController(), message -> { });
    }

    MinestomMenuRuntime(MinestomMenuRenderer renderer, SoundCueService sounds, MenuTickScheduler tickScheduler) {
        this(renderer, sounds, tickScheduler, MinestomMenuRuntime::scheduleOnServerTick, new MenuTraceController(), message -> { });
    }

    MinestomMenuRuntime(MinestomMenuRenderer renderer, SoundCueService sounds, MenuTickScheduler tickScheduler,
                        MenuTraceController traceController, Consumer<String> traceSink) {
        this(renderer, sounds, tickScheduler, MinestomMenuRuntime::scheduleOnServerTick, traceController, traceSink);
    }

    MinestomMenuRuntime(MinestomMenuRenderer renderer, SoundCueService sounds, MenuTickScheduler tickScheduler,
                        Function<Runnable, MenuTickHandle> nextTickScheduler, MenuTraceController traceController,
                        Consumer<String> traceSink) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.tickScheduler = Objects.requireNonNull(tickScheduler, "tickScheduler");
        this.nextTickScheduler = Objects.requireNonNull(nextTickScheduler, "nextTickScheduler");
        this.traceController = Objects.requireNonNull(traceController, "traceController");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
    }

    EventNode<Event> createEventNode(String name) {
        EventNode<Event> node = EventNode.all(name);
        node.addListener(InventoryPreClickEvent.class, this::onInventoryPreClick);
        node.addListener(InventoryCloseEvent.class, this::onInventoryClose);
        node.addListener(PlayerChatEvent.class, this::onPlayerChat);
        node.addListener(PlayerDeathEvent.class, this::onPlayerDeath);
        node.addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        return node;
    }

    void open(Player player, MenuDefinition menu) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        MinestomMenuSession active = sessions.get(player.getUuid());
        MinestomMenuSession callbackOwner = currentUserCallback(player.getUuid());
        if (callbackOwner != null) {
            boolean eligibleOwner = callbackOwner == active || !callbackOwner.admitted();
            if (callbackOwner.deferLifecycle(() -> {
                if (eligibleOwner && sessions.get(player.getUuid()) == active) {
                    open(player, menu);
                }
            })) {
                return;
            }
        }
        if (active != null && active.deferLifecycle(() -> {
            if (sessions.get(player.getUuid()) == active) {
                open(player, menu);
            }
        })) {
            return;
        }
        trace(player, "open", () -> {
            MinestomMenuSession previous = sessions.get(player.getUuid());
            if (previous != null) {
                if (!settleCustody(previous, MenuCustodyGesture.SettleReason.CLOSE)) {
                    return;
                }
            }
            MinestomMenuSession session;
            try {
                session = MenuTrace.time(
                        "runtime.createSession",
                        () -> new MinestomMenuSession(this, player, menu));
            } catch (RuntimeException exception) {
                restoreSettledCustodyView(previous);
                return;
            }
            MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
            if (previous != null) {
                previous.expectManagedClose();
            }
            boolean opened;
            try {
                opened = MenuTrace.<Boolean>time("runtime.show", () -> session.open());
            } catch (RuntimeException exception) {
                restoreAfterFailedRootOpen(player, previous, session);
                if (previous != null) {
                    previous.clearManagedCloseExpectation();
                }
                restoreSettledCustodyView(previous);
                return;
            }
            if (!opened) {
                if (previous != null) {
                    previous.clearManagedCloseExpectation();
                }
                session.detach();
                restoreSettledCustodyView(previous);
                return;
            }
            sessions.put(player.getUuid(), session);
            session.admit();
            if (previous != null) {
                previous.clearManagedCloseExpectation();
                retireInventory(player, previous.inventory());
                MenuTrace.time("runtime.detachPrevious", previous::detach);
            }
            activateTransition(session);
        });
    }

    private void restoreAfterFailedRootOpen(Player player, MinestomMenuSession previous,
                                            MinestomMenuSession attempted) {
        attempted.detach();
        Inventory attemptedInventory = attempted.inventory() != null
                ? attempted.inventory()
                : attempted.attemptedInventory();
        if (player.getOpenInventory() != attemptedInventory) {
            return;
        }
        retireInventory(player, attemptedInventory);
        boolean restored = false;
        if (previous != null) {
            try {
                restored = MenuTrace.time(
                        "runtime.inventoryRestore",
                        () -> player.openInventory(previous.inventory()));
            } catch (RuntimeException ignored) {
                restored = false;
            }
        }
        if (restored) {
            return;
        }
        if (previous != null && sessions.remove(player.getUuid(), previous)) {
            previous.detach();
        }
        if (player.getOpenInventory() == attemptedInventory) {
            player.closeInventory();
        }
    }

    void onInventoryPreClick(InventoryPreClickEvent event) {
        Player player = event.getPlayer();
        AbstractInventory clickedInventory = event.getInventory();
        if (clickedInventory instanceof Inventory inventory
                && isRetiredInventory(player.getUuid(), inventory)) {
            event.setCancelled(true);
            return;
        }
        MinestomMenuSession session = sessions.get(player.getUuid());
        if (session == null) {
            return;
        }
        if (clickedInventory == session.inventory()
                && player.getOpenInventory() != session.inventory()) {
            event.setCancelled(true);
            return;
        }
        if (player.getOpenInventory() != session.inventory()) {
            return;
        }
        if (session.quarantined()) {
            event.setCancelled(true);
            return;
        }
        Inventory inventory = session.inventory();
        if (session.state().reactive()) {
            trace(player, cause(event.getClick()), () -> {
                MenuTrace.field("path", "reactive");
                MenuTrace.field("slot", event.getSlot());
                MenuTrace.title(session.renderedTitle());

                event.setCancelled(true);
                MenuTrace.time("runtime.handleReactiveClick", () -> handleReactiveClick(session, event, inventory));
            });
            return;
        }

        event.setCancelled(true);
        if (event.getInventory() != inventory) {
            return;
        }

        MenuClick click = toCompiledMenuClick(event.getClick());
        if (click == null) {
            return;
        }

        int slot = event.getSlot();
        MenuInteraction interaction = session.state().interaction(slot, click).orElse(null);
        if (interaction == null) {
            return;
        }

        if (!session.tryAcquireInputGuard()) {
            trace(player, cause(event.getClick()), () -> recordSuppressedInput("compiled", slot, click, "tick-cap"));
            return;
        }

        trace(player, cause(event.getClick()), () -> {
            MenuTrace.field("path", "compiled");
            MenuTrace.field("slot", slot);
            MenuTrace.field("button", click);
            MenuTrace.title(session.renderedTitle());

            MenuTrace.time("runtime.handleDirectInteraction", () -> handleDirectInteraction(session, click, interaction));
        });
    }

    void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof Inventory inventory)) {
            return;
        }
        MinestomMenuSession session = sessions.get(event.getPlayer().getUuid());
        if (session == null) {
            return;
        }
        if (session.ignoresManagedClose(inventory)) {
            return;
        }
        if (session.inventory() != inventory) {
            return;
        }
        trace(event.getPlayer(), "close", () -> {
            MenuTrace.title(session.renderedTitle());
            if (ignorePromptDrivenClose(event.getPlayer().getUuid(), session, inventory)) {
                return;
            }
            settleCustodyForForcedExit(session, MenuCustodyGesture.SettleReason.CLOSE);
            retireInventory(event.getPlayer(), inventory);
            if (sessions.remove(event.getPlayer().getUuid(), session)) {
                MenuTrace.time("runtime.sessionDetach", session::detach);
            }
        });
    }

    void onPlayerChat(PlayerChatEvent event) {
        PendingTextPrompt prompt = prompts.get(event.getPlayer().getUuid());
        if (prompt == null || prompt.session() != sessions.get(event.getPlayer().getUuid()) || prompt.mode() != ReactiveTextPromptMode.CHAT) {
            return;
        }
        event.setCancelled(true);
        String message = event.getRawMessage();
        scheduleNextTick(MenuTrace.propagate(() -> completePrompt(prompt,
                "cancel".equalsIgnoreCase(message.trim())
                        ? new ReactiveMenuInput.TextPromptCancelled(prompt.request().key(), ReactiveTextPromptMode.CHAT)
                        : new ReactiveMenuInput.TextPromptSubmitted(prompt.request().key(), message, ReactiveTextPromptMode.CHAT))));
    }

    void onPlayerDisconnect(PlayerDisconnectEvent event) {
        prompts.remove(event.getPlayer().getUuid());
        MinestomMenuSession session = sessions.get(event.getPlayer().getUuid());
        if (session != null) {
            settleCustodyForForcedExit(session, MenuCustodyGesture.SettleReason.DISCONNECT);
            if (sessions.remove(event.getPlayer().getUuid(), session)) {
                session.detach();
            }
        }
        retiredInventories.remove(event.getPlayer().getUuid());
    }

    void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID viewerId = player.getUuid();
        prompts.remove(viewerId);
        MinestomMenuSession session = sessions.get(viewerId);
        if (session == null) {
            return;
        }
        trace(player, "death", () -> {
            Inventory inventory = session.inventory();
            MenuTrace.title(session.renderedTitle());
            settleCustodyForForcedExit(session, MenuCustodyGesture.SettleReason.DEATH);
            retireInventory(player, inventory);
            sessions.remove(viewerId, session);
            session.detach();
            if (player.getOpenInventory() == inventory) {
                player.closeInventory();
            }
        });
    }

    void onTick(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        PendingTextPrompt prompt = prompts.get(session.viewer().getUuid());
        if (prompt != null && prompt.session() == session) {
            return;
        }
        CallbackFence fence = callbackFence(session);
        try {
            trace(session.viewer(), "tick", () -> {
                MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
                MenuTrace.title(session.renderedTitle());
                long revisionBefore = session.state().revision();
                List<ReactiveMenuEffect> effects = MenuTrace.time(
                        "runtime.stateTick",
                        () -> session.userCallback(session.state()::tick));
                if (!callbackCurrent(session, fence)) {
                    return;
                }
                if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, effects))
                        && callbackCurrent(session, fence)
                        && session.state().revision() != revisionBefore) {
                    MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
                }
            });
        } catch (RuntimeException exception) {
            if (callbackOwnerCurrent(session, fence)) {
                quarantineCustodySession(session);
            }
        }
    }

    void refresh(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> refresh(session))) {
            return;
        }
        CallbackFence fence = callbackFence(session);
        try {
            session.userCallback(() -> {
                session.state().invalidateView();
                MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
                return null;
            });
        } catch (RuntimeException exception) {
            if (callbackOwnerCurrent(session, fence)) {
                quarantineCustodySession(session);
            }
        }
    }

    void replace(MinestomMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> replace(session, menu))) {
            return;
        }
        MenuTrace.time("runtime.replace", () -> {
            if (!settleCustody(session, MenuCustodyGesture.SettleReason.NAVIGATE)
                    || sessions.get(session.viewer().getUuid()) != session) {
                return;
            }
            try {
                CallbackFence fence = callbackFence(session);
                var transition = session.userCallback(() -> session.state().prepareOpenChild(menu));
                if (!callbackCurrent(session, fence)) {
                    reconcileFailedNavigation(session);
                    return;
                }
                if (transition.isPresent()
                        && session.applyTransition(transition.orElseThrow())
                        && ownsOpenSession(session)) {
                    activateTransition(session);
                    return;
                }
            } catch (RuntimeException exception) {
                reconcileFailedNavigation(session);
                return;
            }
            reconcileFailedNavigation(session);
        });
    }

    void replaceCurrent(MinestomMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> replaceCurrent(session, menu))) {
            return;
        }
        MenuTrace.time("runtime.replaceCurrent", () -> {
            if (!settleCustody(session, MenuCustodyGesture.SettleReason.NAVIGATE)
                    || sessions.get(session.viewer().getUuid()) != session) {
                return;
            }
            try {
                CallbackFence fence = callbackFence(session);
                MenuSessionState.PreparedTransition transition =
                        session.userCallback(() -> session.state().prepareReplaceCurrent(menu));
                if (!callbackCurrent(session, fence)) {
                    reconcileFailedNavigation(session);
                    return;
                }
                if (session.applyTransition(transition) && ownsOpenSession(session)) {
                    activateTransition(session);
                    return;
                }
            } catch (RuntimeException exception) {
                reconcileFailedNavigation(session);
                return;
            }
            reconcileFailedNavigation(session);
        });
    }

    void back(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> back(session))) {
            return;
        }
        MenuTrace.time("runtime.back", () -> {
            if (!settleCustody(session, MenuCustodyGesture.SettleReason.NAVIGATE)
                    || sessions.get(session.viewer().getUuid()) != session) {
                return;
            }
            try {
                CallbackFence fence = callbackFence(session);
                var transition = session.userCallback(session.state()::prepareBack);
                if (!callbackCurrent(session, fence)) {
                    reconcileFailedNavigation(session);
                    return;
                }
                if (transition.isPresent()
                        && session.applyTransition(transition.orElseThrow())
                        && ownsOpenSession(session)) {
                    activateTransition(session);
                    return;
                }
            } catch (RuntimeException exception) {
                reconcileFailedNavigation(session);
                return;
            }
            reconcileFailedNavigation(session);
        });
    }

    void close(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> close(session))) {
            return;
        }
        close(session, MenuCustodyGesture.SettleReason.CLOSE);
    }

    private void close(MinestomMenuSession session, MenuCustodyGesture.SettleReason reason) {
        if (reason == MenuCustodyGesture.SettleReason.SHUTDOWN) {
            settleCustodyForForcedExit(session, reason);
        } else if (!settleCustody(session, reason)) {
            return;
        }
        if (!sessions.remove(session.viewer().getUuid(), session)) {
            return;
        }
        prompts.remove(session.viewer().getUuid());
        retireInventory(session.viewer(), session.inventory());
        MenuTrace.time("runtime.close", () -> {
            session.detach();
            MenuTrace.time("runtime.inventoryClose", () -> {
                session.viewer().closeInventory();
            });
        });
    }

    void render(Inventory inventory, List<MenuSlot> previousSlots, List<MenuSlot> nextSlots,
                MenuCustodyLedger<ItemStack> custodyLedger) {
        long started = System.nanoTime();
        int changedSlots = 0;
        for (int slot = 0; slot < nextSlots.size(); slot++) {
            String custodyTarget = custodyLedger.targetAt(slot).orElse(null);
            if (custodyTarget != null && custodyLedger.target(custodyTarget).isPresent()) {
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
                inventory.setItemStack(renderedSlot, rendered);
                long patchElapsed = System.nanoTime() - patchStarted;
                MenuTrace.addDuration("runtime.slotPatch", patchElapsed);
                MenuTrace.detailIfSlow("slot-patch", patchElapsed,
                        () -> "slot=" + renderedSlot + " title=" + flatten(nextSlot.title()));
            }
        }
        MenuTrace.setCount("changedSlots", changedSlots);
        MenuTrace.addDuration("runtime.inventoryPatch", System.nanoTime() - started);
    }

    void renderCustodyTargets(Inventory inventory, List<MenuSlot> slots, MenuCustodyLedger<ItemStack> ledger) {
        ledger.targetSlots().forEach((key, slot) -> {
            if (ledger.target(key).isEmpty()) {
                inventory.setItemStack(slot, renderer.render(slots.get(slot)));
            }
        });
    }

    boolean validateCustodyView(MinestomMenuSession session, Inventory inventory,
                                List<MenuSlot> renderedSlots, MenuCustodyLedger<ItemStack> ledger) {
        for (Map.Entry<String, Integer> target : ledger.targetSlots().entrySet()) {
            int slot = target.getValue();
            ItemStack expected = ledger.target(target.getKey())
                    .map(MenuCustodyLedger.Entry::nativeItem)
                    .orElseGet(() -> renderer.render(renderedSlots.get(slot)));
            if (!inventory.getItemStack(slot).equals(expected)) {
                quarantineNativeDrift(session);
                return false;
            }
        }
        return true;
    }

    MenuTickScheduler tickScheduler() {
        return tickScheduler;
    }

    @Override
    public void close() {
        List.copyOf(sessions.values()).forEach(session ->
                close(session, MenuCustodyGesture.SettleReason.SHUTDOWN));
        sessions.clear();
        retiredInventories.clear();
    }

    private void handleReactiveClick(MinestomMenuSession session, InventoryPreClickEvent event, Inventory inventory) {
        Click click = event.getClick();
        if (click instanceof Click.LeftDrag leftDrag) {
            if (session.custodyEnabled()) {
                handleCustodyDrag(session, MenuClick.LEFT, leftDrag.slots(), inventory);
            }
            return;
        }
        if (click instanceof Click.RightDrag rightDrag) {
            if (session.custodyEnabled()) {
                handleCustodyDrag(session, MenuClick.RIGHT, rightDrag.slots(), inventory);
            }
            return;
        }
        if (click instanceof Click.LeftDropCursor) {
            if (session.custodyEnabled()) {
                handleCustodyOutside(session, MenuClick.LEFT);
            }
            return;
        }
        if (click instanceof Click.RightDropCursor) {
            if (session.custodyEnabled()) {
                handleCustodyOutside(session, MenuClick.RIGHT);
            }
            return;
        }

        MenuClick button = toReactiveMenuClick(click);
        if (button == null) {
            return;
        }
        MenuTrace.field("button", button);

        AbstractInventory clickedInventory = event.getInventory();
        int slot = event.getSlot();
        if (clickedInventory == inventory && slot >= 0 && slot < inventory.getSize()) {
            String custodyTarget = session.state().custodyTargetAt(slot).orElse(null);
            if (custodyTarget != null) {
                if (!session.tryAcquireInputGuard()) {
                    trace(session.viewer(), cause(click), () ->
                            recordSuppressedInput("custody-target", slot, button, "tick-cap"));
                    return;
                }
                handleCustodyTarget(session, new MenuCustodyGesture.TargetClick(
                        custodyTarget,
                        button,
                        isShiftClick(click)));
                return;
            }
            MenuInteraction interaction = session.state().interaction(slot, button).orElse(null);
            if (interaction != null && !(interaction.action() instanceof MenuSlotAction.Dispatch)) {
                if (!session.tryAcquireInputGuard()) {
                    trace(session.viewer(), cause(click), () -> recordSuppressedInput("reactive-top", slot, button, "tick-cap"));
                    return;
                }
                handleDirectInteraction(session, button, interaction);
                return;
            }
            if (interaction == null && !session.state().acceptsReactiveClick(slot)) {
                return;
            }
            if (!session.tryAcquireInputGuard()) {
                trace(session.viewer(), cause(click), () -> recordSuppressedInput("reactive-top", slot, button, "tick-cap"));
                return;
            }
            Object message = interaction != null ? ((MenuSlotAction.Dispatch) interaction.action()).message() : null;
            handleReactiveInput(session, new ReactiveMenuInput.Click(
                    slot,
                    button,
                    isShiftClick(click),
                    message),
                    interaction);
            return;
        }

        if (slot < 0) {
            return;
        }
        if (clickedInventory != inventory && !(clickedInventory instanceof PlayerInventory)) {
            return;
        }

        int bottomSlot = clickedInventory == inventory ? slot - inventory.getSize() : slot;
        if (bottomSlot < 0) {
            return;
        }
        if (!session.custodyEnabled()) {
            return;
        }
        if (!session.tryAcquireInputGuard()) {
            trace(session.viewer(), cause(click), () -> recordSuppressedInput("reactive-bottom", bottomSlot, button, "tick-cap"));
            return;
        }
        handleCustodyViewer(session, bottomSlot, button, isShiftClick(click));
    }

    private void handleCustodyViewer(MinestomMenuSession session, int slot, MenuClick button, boolean shift) {
        PlayerInventory viewerInventory = session.viewer().getInventory();
        CallbackFence fence = callbackFence(session);
        MenuCustodyLedger<ItemStack> ledger = fence.custodyLedger();
        ItemStack viewerItem = slot < VIEWER_STORAGE_SIZE
                ? viewerInventory.getItemStack(slot)
                : ItemStack.AIR;
        MenuViewerSlot observation = new MenuViewerSlot(
                session.nextViewerObservationId(),
                slot,
                toMenuStack(viewerItem));
        MenuCustodyGesture gesture = new MenuCustodyGesture.ViewerClick(observation, button, shift);
        long operationId = ledger.nextOperationId();
        MenuCustodyDecision.Move move = decideCustody(session, operationId, gesture, fence);
        if (move == null) {
            return;
        }

        if (slot >= VIEWER_STORAGE_SIZE) {
            rejectCustody(session, operationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        if (ledger.cursor().isPresent()) {
            if (!viewerItem.isAir()) {
                rejectCustody(session, operationId, gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
                return;
            }
            if (!(move.destination() instanceof MenuCustodyDestination.ViewerSlot destination)
                    || !sameObservation(observation, destination.slot())) {
                rejectCustody(session, operationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
                return;
            }
            transferCustody(
                    session,
                    gesture,
                    new MenuCustodyLedger.Source.Cursor(),
                    move.destination(),
                    observation,
                    operationId,
                    fence);
            return;
        }

        if (viewerItem.isAir() || !viewerInventory.getCursorItem().isAir()) {
            rejectCustody(session, operationId, gesture,
                    viewerItem.isAir() ? MenuCustodyFailure.INVALID_DESTINATION : MenuCustodyFailure.OCCUPIED_DESTINATION);
            return;
        }
        acquireCustody(session, gesture, observation, viewerItem, move.destination(), operationId, fence);
    }

    private void handleCustodyTarget(MinestomMenuSession session, MenuCustodyGesture.TargetClick gesture) {
        CallbackFence fence = callbackFence(session);
        MenuCustodyLedger<ItemStack> ledger = fence.custodyLedger();
        long operationId = ledger.nextOperationId();
        MenuCustodyDecision.Move move = decideCustody(session, operationId, gesture, fence);
        if (move == null) {
            return;
        }
        boolean hasTarget = ledger.target(gesture.targetKey()).isPresent();
        boolean hasCursor = ledger.cursor().isPresent();
        if (hasTarget == hasCursor) {
            rejectCustody(session, operationId, gesture,
                    hasTarget ? MenuCustodyFailure.OCCUPIED_DESTINATION : MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        MenuCustodyLedger.Source source;
        if (hasCursor) {
            if (!(move.destination() instanceof MenuCustodyDestination.Target target)
                    || !target.key().equals(gesture.targetKey())) {
                rejectCustody(session, operationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
                return;
            }
            source = new MenuCustodyLedger.Source.Cursor();
        } else {
            if (!(move.destination() instanceof MenuCustodyDestination.Cursor)
                    && !(move.destination() instanceof MenuCustodyDestination.Origin)) {
                rejectCustody(session, operationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
                return;
            }
            source = new MenuCustodyLedger.Source.Target(gesture.targetKey());
        }
        transferCustody(session, gesture, source, move.destination(), null, operationId, fence);
    }

    private void handleCustodyDrag(MinestomMenuSession session, MenuClick button, List<Integer> slots,
                                   Inventory inventory) {
        List<String> targets = slots.stream()
                .filter(slot -> slot >= 0 && slot < inventory.getSize())
                .map(slot -> session.state().custodyTargetAt(slot).orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        if (!session.tryAcquireInputGuard()) {
            trace(session.viewer(), "custody-drag", () ->
                    recordSuppressedInput("custody-drag", -1, button, "tick-cap"));
            return;
        }
        MenuCustodyGesture gesture = new MenuCustodyGesture.TargetDrag(targets, button);
        CallbackFence fence = callbackFence(session);
        MenuCustodyLedger<ItemStack> ledger = fence.custodyLedger();
        long operationId = ledger.nextOperationId();
        if (targets.size() != 1 || slots.size() != 1 || ledger.cursor().isEmpty()) {
            rejectCustody(session, operationId, gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
            return;
        }
        MenuCustodyDecision.Move move = decideCustody(session, operationId, gesture, fence);
        if (move == null) {
            return;
        }
        if (!(move.destination() instanceof MenuCustodyDestination.Target target)
                || !targets.contains(target.key())) {
            rejectCustody(session, operationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        transferCustody(
                session,
                gesture,
                new MenuCustodyLedger.Source.Cursor(),
                move.destination(),
                null,
                operationId,
                fence);
    }

    private void handleCustodyOutside(MinestomMenuSession session, MenuClick button) {
        if (!session.tryAcquireInputGuard()) {
            trace(session.viewer(), "custody-outside", () ->
                    recordSuppressedInput("custody-outside", -1, button, "tick-cap"));
            return;
        }
        MenuCustodyGesture gesture = new MenuCustodyGesture.OutsideClick(button);
        CallbackFence fence = callbackFence(session);
        MenuCustodyLedger<ItemStack> ledger = fence.custodyLedger();
        long operationId = ledger.nextOperationId();
        if (ledger.cursor().isEmpty()) {
            rejectCustody(session, operationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        MenuCustodyDecision.Move move = decideCustody(session, operationId, gesture, fence);
        if (move == null) {
            return;
        }
        if (!(move.destination() instanceof MenuCustodyDestination.Origin)) {
            rejectCustody(session, operationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        transferCustody(
                session,
                gesture,
                new MenuCustodyLedger.Source.Cursor(),
                move.destination(),
                null,
                operationId,
                fence);
    }

    private MenuCustodyDecision.Move decideCustody(MinestomMenuSession session, long operationId,
                                                   MenuCustodyGesture gesture, CallbackFence fence) {
        MenuCustodyDecision decision;
        try {
            decision = session.userCallback(() ->
                    session.state().decideCustody(gesture, fence.custodyLedger().snapshot()));
        } catch (RuntimeException exception) {
            if (custodyCallbackCurrent(session, fence)) {
                rejectCustody(session, operationId, gesture, MenuCustodyFailure.POLICY_REJECTED);
            }
            return null;
        }
        if (!custodyCallbackCurrent(session, fence)) {
            return null;
        }
        if (decision instanceof MenuCustodyDecision.Move move) {
            return move;
        }
        rejectCustody(session, operationId, gesture, MenuCustodyFailure.POLICY_REJECTED);
        return null;
    }

    private void acquireCustody(MinestomMenuSession session, MenuCustodyGesture gesture,
                                MenuViewerSlot observation, ItemStack nativeItem,
                                MenuCustodyDestination destination, long rejectedOperationId,
                                CallbackFence fence) {
        if (!custodyCallbackCurrent(session, fence)) {
            return;
        }
        MenuCustodyLedger<ItemStack> ledger = fence.custodyLedger();
        MenuCustodyLedger.Destination ledgerDestination = toLedgerDestination(
                session,
                destination,
                observation,
                null);
        if (ledgerDestination == null || ledgerDestination instanceof MenuCustodyLedger.Destination.Released) {
            rejectCustody(session, rejectedOperationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = ledger.prepareAcquire(
                    nativeItem,
                    Objects.requireNonNull(toMenuStack(nativeItem), "custody presentation"),
                    observation.slot(),
                    ledgerDestination);
        } catch (RuntimeException exception) {
            rejectCustody(session, rejectedOperationId, gesture, failureFor(exception));
            return;
        }

        NativeBeforeImages before = new NativeBeforeImages(session, -1);
        before.captureViewer(observation.slot());
        before.captureDestination(ledgerDestination);
        try {
            ItemStack current = session.viewer().getInventory().getItemStack(observation.slot());
            if (!current.equals(nativeItem)) {
                quarantineNativeDrift(session);
                return;
            }
            if (!nativeDestinationReady(session, ledgerDestination, -1)) {
                quarantineNativeDrift(session);
                return;
            }
            before.clearViewer(observation.slot());
            before.writeDestination(ledgerDestination, nativeItem);
            ledger.commit(transfer);
        } catch (RuntimeException exception) {
            if (!before.rollback()) {
                quarantineNativeDrift(session);
                return;
            }
            rejectCustody(session, transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            return;
        }
        commitCustody(session, transfer.operationId(), gesture);
    }

    private void transferCustody(MinestomMenuSession session, MenuCustodyGesture gesture,
                                 MenuCustodyLedger.Source source, MenuCustodyDestination destination,
                                 MenuViewerSlot observation, long rejectedOperationId,
                                 CallbackFence fence) {
        if (!custodyCallbackCurrent(session, fence)) {
            return;
        }
        MenuCustodyLedger<ItemStack> ledger = fence.custodyLedger();
        MenuCustodyLedger.Entry<ItemStack> entry = switch (source) {
            case MenuCustodyLedger.Source.Cursor ignored -> ledger.cursor().orElse(null);
            case MenuCustodyLedger.Source.Target target -> ledger.target(target.key()).orElse(null);
        };
        if (entry == null) {
            quarantineNativeDrift(session);
            return;
        }
        int releasedSlot = releaseDestinationSlot(session, destination, observation, entry);
        MenuCustodyLedger.Destination ledgerDestination = toLedgerDestination(
                session,
                destination,
                observation,
                entry);
        if (ledgerDestination == null) {
            rejectCustody(session, rejectedOperationId, gesture, MenuCustodyFailure.INVALID_DESTINATION);
            return;
        }
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = ledger.prepareTransfer(source, ledgerDestination);
        } catch (RuntimeException exception) {
            rejectCustody(session, rejectedOperationId, gesture, failureFor(exception));
            return;
        }

        NativeBeforeImages before = new NativeBeforeImages(session, releasedSlot);
        before.captureSource(source);
        before.captureDestination(ledgerDestination);
        try {
            if (!before.sourceMatches(source, entry.nativeItem())) {
                quarantineNativeDrift(session);
                return;
            }
            if (!nativeDestinationReady(session, ledgerDestination, releasedSlot)) {
                quarantineNativeDrift(session);
                return;
            }
            before.clearSource(source);
            before.writeDestination(ledgerDestination, entry.nativeItem());
            ledger.commit(transfer);
        } catch (RuntimeException exception) {
            if (!before.rollback()) {
                quarantineNativeDrift(session);
                return;
            }
            rejectCustody(session, transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            return;
        }
        commitCustody(session, transfer.operationId(), gesture);
    }

    private MenuCustodyLedger.Destination toLedgerDestination(
            MinestomMenuSession session,
            MenuCustodyDestination destination,
            MenuViewerSlot observation,
            MenuCustodyLedger.Entry<ItemStack> entry
    ) {
        return switch (destination) {
            case MenuCustodyDestination.Cursor ignored -> new MenuCustodyLedger.Destination.Cursor();
            case MenuCustodyDestination.Target target ->
                    session.custodyLedger().targetSlots().containsKey(target.key())
                            ? new MenuCustodyLedger.Destination.Target(target.key())
                            : null;
            case MenuCustodyDestination.ViewerSlot viewerSlot ->
                    releaseDestinationSlot(session, viewerSlot, observation, entry) >= 0
                            ? new MenuCustodyLedger.Destination.Released()
                            : null;
            case MenuCustodyDestination.Origin ignored ->
                    releaseDestinationSlot(session, destination, observation, entry) >= 0
                            ? new MenuCustodyLedger.Destination.Released()
                            : null;
        };
    }

    private static int releaseDestinationSlot(
            MinestomMenuSession session,
            MenuCustodyDestination destination,
            MenuViewerSlot observation,
            MenuCustodyLedger.Entry<ItemStack> entry
    ) {
        if (destination instanceof MenuCustodyDestination.ViewerSlot viewerSlot) {
            if (observation == null
                    || !sameObservation(observation, viewerSlot.slot())
                    || !session.viewer().getInventory().getItemStack(observation.slot()).isAir()) {
                return -1;
            }
            return observation.slot();
        }
        if (destination instanceof MenuCustodyDestination.Origin && entry != null) {
            int originalSlot = entry.originalViewerSlot();
            return originalSlot >= 0
                    && originalSlot < VIEWER_STORAGE_SIZE
                    && session.viewer().getInventory().getItemStack(originalSlot).isAir()
                    ? originalSlot
                    : -1;
        }
        return -1;
    }

    private void commitCustody(MinestomMenuSession session, long operationId, MenuCustodyGesture gesture) {
        if (session.quarantined()) {
            return;
        }
        try {
            handleReactiveInput(session, new ReactiveMenuInput.CustodyCommitted(
                    operationId,
                    gesture,
                    session.custodyLedger().snapshot()), null);
        } catch (RuntimeException exception) {
            quarantineCustodySession(session);
        }
    }

    private boolean commitSettledCustody(
            MinestomMenuSession session,
            long operationId,
            MenuCustodyGesture gesture
    ) {
        if (session.quarantined()) {
            return false;
        }
        CallbackFence fence = callbackFence(session);
        try {
            MenuTrace.time(
                    "runtime.reactiveDispatch",
                    () -> session.userCallback(() -> session.state().dispatchReactive(
                            new ReactiveMenuInput.CustodyCommitted(
                                    operationId,
                                    gesture,
                                    session.custodyLedger().snapshot()))));
            session.markSettledCustodyViewDirty();
            return callbackCurrent(session, fence);
        } catch (RuntimeException exception) {
            if (callbackOwnerCurrent(session, fence)) {
                quarantineCustodySession(session);
            }
            return false;
        }
    }

    private void rejectCustody(MinestomMenuSession session, long operationId, MenuCustodyGesture gesture,
                               MenuCustodyFailure failure) {
        if (session.quarantined()) {
            return;
        }
        try {
            handleReactiveInput(session, new ReactiveMenuInput.CustodyRejected(
                    operationId,
                    gesture,
                    failure,
                    session.custodyLedger().snapshot()), null);
        } catch (RuntimeException exception) {
            quarantineCustodySession(session);
        }
    }

    private void quarantineCustodySession(MinestomMenuSession session) {
        session.quarantine();
        MenuTrace.field("custodySession", "quarantined");
        settleCustodyForForcedExit(session, MenuCustodyGesture.SettleReason.SHUTDOWN);
        finishCustodyQuarantine(session);
    }

    private void quarantineNativeDrift(MinestomMenuSession session) {
        session.quarantine();
        MenuTrace.field("custodySession", "native-drift");
        tombstoneCustody(session);
        finishCustodyQuarantine(session);
    }

    private void finishCustodyQuarantine(MinestomMenuSession session) {
        prompts.remove(session.viewer().getUuid());
        retireInventory(session.viewer(), session.inventory());
        boolean active = sessions.remove(session.viewer().getUuid(), session);
        session.detach();
        if (active && session.viewer().getOpenInventory() == session.inventory()) {
            session.viewer().closeInventory();
        }
    }

    private static boolean sameObservation(MenuViewerSlot expected, MenuViewerSlot actual) {
        return expected.observationId() == actual.observationId()
                && expected.slot() == actual.slot()
                && Objects.equals(expected.item(), actual.item());
    }

    private static MenuCustodyFailure failureFor(RuntimeException exception) {
        return exception instanceof IllegalStateException
                ? MenuCustodyFailure.OCCUPIED_DESTINATION
                : MenuCustodyFailure.INVALID_DESTINATION;
    }

    private static int releaseSlot(Player player, int preferredSlot) {
        PlayerInventory inventory = player.getInventory();
        if (preferredSlot >= 0
                && preferredSlot < VIEWER_STORAGE_SIZE
                && inventory.getItemStack(preferredSlot).isAir()) {
            return preferredSlot;
        }
        for (int slot = 0; slot < VIEWER_STORAGE_SIZE; slot++) {
            if (inventory.getItemStack(slot).isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private boolean nativeDestinationReady(MinestomMenuSession session,
                                           MenuCustodyLedger.Destination destination,
                                           int releasedSlot) {
        return switch (destination) {
            case MenuCustodyLedger.Destination.Cursor ignored ->
                    session.viewer().getInventory().getCursorItem().isAir();
            case MenuCustodyLedger.Destination.Target target -> {
                Integer slot = session.custodyLedger().targetSlots().get(target.key());
                if (slot == null) {
                    yield false;
                }
                MenuSlot baseSlot = session.state().slot(slot).orElse(null);
                yield baseSlot != null
                        && session.inventory().getItemStack(slot).equals(renderer.render(baseSlot));
            }
            case MenuCustodyLedger.Destination.Released ignored ->
                    releasedSlot >= 0
                            && releasedSlot < VIEWER_STORAGE_SIZE
                            && session.viewer().getInventory().getItemStack(releasedSlot).isAir();
        };
    }

    private boolean settleCustody(MinestomMenuSession session, MenuCustodyGesture.SettleReason reason) {
        MenuCustodyLedger<ItemStack> ledger = session.custodyLedger();
        if (ledger.empty()) {
            return true;
        }
        MenuCustodyGesture gesture = new MenuCustodyGesture.Settle(reason);
        long operationId = ledger.nextOperationId();
        if (ledger.cursor().isPresent()) {
            Long released = releaseCustodyEntry(
                    session,
                    new MenuCustodyLedger.Source.Cursor(),
                    gesture,
                    operationId);
            if (released == null) {
                return false;
            }
            operationId = released;
        }
        for (String key : List.copyOf(ledger.targetSlots().keySet())) {
            if (ledger.target(key).isEmpty()) {
                continue;
            }
            Long released = releaseCustodyEntry(
                    session,
                    new MenuCustodyLedger.Source.Target(key),
                    gesture,
                    operationId);
            if (released == null) {
                return false;
            }
            operationId = released;
        }
        return commitSettledCustody(session, operationId, gesture) && ledger.empty();
    }

    private void settleCustodyForForcedExit(MinestomMenuSession session,
                                            MenuCustodyGesture.SettleReason reason) {
        boolean settled;
        try {
            settled = settleCustody(session, reason);
        } catch (RuntimeException exception) {
            settled = false;
        }
        if (settled) {
            return;
        }
        MenuTrace.field("custodySettlement", "tombstoned");
        tombstoneCustody(session);
    }

    private static void tombstoneCustody(MinestomMenuSession session) {
        MenuCustodyLedger<ItemStack> ledger = session.custodyLedger();
        ledger.cursor().ifPresent(entry -> {
            PlayerInventory inventory = session.viewer().getInventory();
            if (inventory.getCursorItem().equals(entry.nativeItem())) {
                try {
                    inventory.setCursorItem(ItemStack.AIR);
                } catch (RuntimeException ignored) {
                    // Best effort: the session is retired even when the host rejects a final tombstone write.
                }
            }
        });
        ledger.targetSlots().forEach((key, slot) -> ledger.target(key).ifPresent(entry -> {
            Inventory inventory = session.inventory();
            if (inventory != null && inventory.getItemStack(slot).equals(entry.nativeItem())) {
                try {
                    inventory.setItemStack(slot, ItemStack.AIR);
                } catch (RuntimeException ignored) {
                    // Best effort: the retired inventory remains blocked by its identity tombstone.
                }
            }
        }));
    }

    private Long releaseCustodyEntry(MinestomMenuSession session, MenuCustodyLedger.Source source,
                                     MenuCustodyGesture gesture, long rejectedOperationId) {
        MenuCustodyLedger<ItemStack> ledger = session.custodyLedger();
        MenuCustodyLedger.Entry<ItemStack> entry = switch (source) {
            case MenuCustodyLedger.Source.Cursor ignored -> ledger.cursor().orElse(null);
            case MenuCustodyLedger.Source.Target target -> ledger.target(target.key()).orElse(null);
        };
        if (entry == null) {
            quarantineNativeDrift(session);
            return null;
        }
        int destinationSlot = releaseSlot(session.viewer(), entry.originalViewerSlot());
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = ledger.prepareTransfer(source, new MenuCustodyLedger.Destination.Released());
        } catch (RuntimeException exception) {
            rejectCustody(session, rejectedOperationId, gesture, failureFor(exception));
            return null;
        }

        NativeBeforeImages before = new NativeBeforeImages(session, destinationSlot);
        before.captureSource(source);
        if (destinationSlot >= 0) {
            before.captureDestination(new MenuCustodyLedger.Destination.Released());
        }
        try {
            if (!before.sourceMatches(source, entry.nativeItem())) {
                quarantineNativeDrift(session);
                return null;
            }
            if (destinationSlot >= 0
                    && !nativeDestinationReady(
                    session,
                    new MenuCustodyLedger.Destination.Released(),
                    destinationSlot)) {
                quarantineNativeDrift(session);
                return null;
            }
            before.clearSource(source);
            if (destinationSlot >= 0) {
                before.writeDestination(new MenuCustodyLedger.Destination.Released(), entry.nativeItem());
            }
            ledger.commit(transfer);
        } catch (RuntimeException exception) {
            if (!before.rollback()) {
                quarantineNativeDrift(session);
                return null;
            }
            rejectCustody(session, transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            return null;
        }
        if (destinationSlot < 0) {
            if (!spawnOverflowItem(session.viewer(), entry.nativeItem())) {
                abandonSession(session);
                return null;
            }
        }
        return transfer.operationId();
    }

    private static boolean spawnOverflowItem(Player viewer, ItemStack itemStack) {
        var instance = viewer.getInstance();
        if (instance == null) {
            return false;
        }
        ItemEntity droppedItem = new ItemEntity(itemStack);
        try {
            // This is lifecycle restitution, not a player drop gesture. Player#dropItem
            // only dispatches ItemDropEvent, whose listeners do not prove delivery.
            // Once setInstance begins, custody is never restored, removed, or retried.
            return droppedItem.setInstance(
                    instance,
                    viewer.getPosition().add(0.0, 0.5, 0.0)) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void handleReactiveInput(MinestomMenuSession session, ReactiveMenuInput input, MenuInteraction interaction) {
        CallbackFence fence = callbackFence(session);
        long revisionBefore = session.state().revision();
        try {
            if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
                playInteractionSound(session.viewer(), interaction);
            }
            List<ReactiveMenuEffect> effects = MenuTrace.time(
                    "runtime.reactiveDispatch",
                    () -> session.userCallback(() -> session.state().dispatchReactive(input)));
            if (!callbackCurrent(session, fence)) {
                return;
            }
            if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, effects))
                    && callbackCurrent(session, fence)) {
                if (input instanceof ReactiveMenuInput.CustodyCommitted) {
                    MenuTrace.time("session.renderCurrentView", session::renderAfterCustodyCommit);
                } else if (session.state().revision() != revisionBefore) {
                    MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
                }
            }
        } catch (RuntimeException exception) {
            if (callbackOwnerCurrent(session, fence)) {
                quarantineCustodySession(session);
            }
        }
    }

    private void handleDirectInteraction(MinestomMenuSession session, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(session.viewer(), interaction);
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> {
                if (settleCustody(session, MenuCustodyGesture.SettleReason.NAVIGATE)) {
                    try {
                        CallbackFence fence = callbackFence(session);
                        var transition = session.userCallback(
                                () -> session.state().prepareOpenFrame(openFrame.frameId()));
                        if (!callbackCurrent(session, fence)) {
                            reconcileFailedNavigation(session);
                            return;
                        }
                        if (transition.isPresent()
                                && session.applyTransition(transition.orElseThrow())
                                && ownsOpenSession(session)) {
                            return;
                        }
                    } catch (RuntimeException exception) {
                        reconcileFailedNavigation(session);
                        return;
                    }
                    reconcileFailedNavigation(session);
                }
            }
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                CallbackFence fence = callbackFence(session);
                try {
                    session.userCallback(() -> {
                        MenuContext context = new MenuContext(
                                click,
                                session.state().frameId(),
                                session.state().values(),
                                session);
                        execute.action().execute(context);
                        return null;
                    });
                    if (callbackCurrent(session, fence)) {
                        session.renderCurrentView();
                    }
                } catch (RuntimeException exception) {
                    if (callbackOwnerCurrent(session, fence)) {
                        quarantineCustodySession(session);
                    }
                }
            }
            case MenuSlotAction.Dispatch ignored -> {
            }
        }
    }

    private boolean applyEffects(MinestomMenuSession session, List<ReactiveMenuEffect> effects) {
        for (ReactiveMenuEffect effect : effects) {
            switch (effect) {
                case ReactiveMenuEffect.RequestTextPrompt prompt -> {
                    return openTextPrompt(session, prompt.request());
                }
                case ReactiveMenuEffect.Open open -> {
                    replace(session, open.menu());
                    return true;
                }
                case ReactiveMenuEffect.Replace replace -> {
                    replaceCurrent(session, replace.menu());
                    return true;
                }
                case ReactiveMenuEffect.Close ignored -> {
                    close(session);
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    void rearmInputGuard(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        MenuTrace.time("runtime.inputGuardRearm", session::rearmInputGuard);
    }

    MenuTickHandle scheduleNextTick(Runnable action) {
        return MenuTrace.time("runtime.inputGuardScheduleTask", () -> nextTickScheduler.apply(Objects.requireNonNull(action, "action")));
    }

    void scheduleDeferredLifecycle(List<Runnable> actions) {
        List<Runnable> deferred = List.copyOf(actions);
        scheduleNextTick(MenuTrace.propagate(() -> deferred.forEach(Runnable::run)));
    }

    CallbackFence callbackFence(MinestomMenuSession session) {
        return new CallbackFence(
                sessions.get(session.viewer().getUuid()),
                session.inventory(),
                session.viewer().getOpenInventory(),
                session.custodyLedger(),
                session.actionVersion(),
                session.inventoryGeneration(),
                session.lifecycleGeneration());
    }

    boolean callbackCurrent(MinestomMenuSession session, CallbackFence fence) {
        return fence.mappedSession() == session && callbackFrameCurrent(session, fence);
    }

    boolean callbackFrameCurrent(MinestomMenuSession session, CallbackFence fence) {
        return !session.quarantined()
                && sessions.get(session.viewer().getUuid()) == fence.mappedSession()
                && session.inventory() == fence.inventory()
                && session.viewer().getOpenInventory() == fence.openInventory()
                && session.custodyLedger() == fence.custodyLedger()
                && session.actionVersion() == fence.actionVersion()
                && session.inventoryGeneration() == fence.inventoryGeneration()
                && session.lifecycleGeneration() == fence.lifecycleGeneration();
    }

    private boolean ownsOpenSession(MinestomMenuSession session) {
        return !session.quarantined()
                && sessions.get(session.viewer().getUuid()) == session
                && session.viewer().getOpenInventory() == session.inventory();
    }

    private void restoreSettledCustodyView(MinestomMenuSession session) {
        if (session == null || !ownsOpenSession(session)) {
            return;
        }
        try {
            boolean restored = MenuTrace.time(
                    "session.restoreSettledCustodyView",
                    session::restoreSettledCustodyView);
            if (!restored && sessions.get(session.viewer().getUuid()) == session) {
                quarantineCustodySession(session);
            }
        } catch (RuntimeException exception) {
            if (sessions.get(session.viewer().getUuid()) == session) {
                quarantineCustodySession(session);
            }
        }
    }

    private void reconcileFailedNavigation(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        if (ownsOpenSession(session)) {
            restoreSettledCustodyView(session);
            return;
        }
        quarantineCustodySession(session);
    }

    private boolean callbackOwnerCurrent(MinestomMenuSession session, CallbackFence fence) {
        return !session.quarantined()
                && fence.mappedSession() == session
                && sessions.get(session.viewer().getUuid()) == session
                && session.custodyLedger() == fence.custodyLedger();
    }

    private boolean custodyCallbackCurrent(MinestomMenuSession session, CallbackFence fence) {
        return fence.openInventory() == fence.inventory() && callbackCurrent(session, fence);
    }

    void enterUserCallback(MinestomMenuSession session) {
        userCallbackSessions.get().addLast(session);
    }

    void exitUserCallback(MinestomMenuSession session) {
        ArrayDeque<MinestomMenuSession> callbacks = userCallbackSessions.get();
        if (!callbacks.isEmpty() && callbacks.peekLast() == session) {
            callbacks.removeLast();
        } else {
            callbacks.removeLastOccurrence(session);
        }
        if (callbacks.isEmpty()) {
            userCallbackSessions.remove();
        }
    }

    private MinestomMenuSession currentUserCallback(UUID viewerId) {
        var callbacks = userCallbackSessions.get().descendingIterator();
        while (callbacks.hasNext()) {
            MinestomMenuSession session = callbacks.next();
            if (session.viewer().getUuid().equals(viewerId)) {
                return session;
            }
        }
        return null;
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

    private void activateTransition(MinestomMenuSession session) {
        if (!session.state().reactive()) {
            return;
        }
        CallbackFence fence = callbackFence(session);
        long revisionBefore = session.state().revision();
        try {
            List<ReactiveMenuEffect> effects = MenuTrace.time(
                    "runtime.stateOpened",
                    () -> session.userCallback(session.state()::opened));
            if (!callbackCurrent(session, fence)) {
                return;
            }
            if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, effects))
                    && callbackCurrent(session, fence)
                    && session.state().revision() != revisionBefore) {
                MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
            }
        } catch (RuntimeException exception) {
            if (callbackOwnerCurrent(session, fence)) {
                quarantineCustodySession(session);
            }
        }
    }

    private MinestomMenuSession session(Player player, Inventory inventory) {
        MinestomMenuSession session = sessions.get(player.getUuid());
        if (session == null || session.inventory() != inventory) {
            return null;
        }
        return session;
    }

    private boolean openTextPrompt(MinestomMenuSession session, ReactiveTextPromptRequest request) {
        if (!settleCustody(session, MenuCustodyGesture.SettleReason.PROMPT)) {
            return false;
        }
        if (sessions.get(session.viewer().getUuid()) != session) {
            return false;
        }
        prompts.put(session.viewer().getUuid(), new PendingTextPrompt(session, request, ReactiveTextPromptMode.CHAT, true));
        session.viewer().closeInventory();
        session.viewer().sendMessage(Component.text(request.prompt() + " Type your response in chat or send 'cancel' to keep the current value."));
        return true;
    }

    private boolean ignorePromptDrivenClose(UUID viewerId, MinestomMenuSession session, Inventory inventory) {
        PendingTextPrompt prompt = prompts.get(viewerId);
        if (prompt == null || prompt.session() != session || !prompt.awaitingMenuClose() || session.inventory() != inventory) {
            return false;
        }
        prompt.awaitingMenuClose(false);
        return true;
    }

    private void completePrompt(PendingTextPrompt prompt, ReactiveMenuInput input) {
        UUID viewerId = prompt.session().viewer().getUuid();
        if (prompts.get(viewerId) != prompt) {
            return;
        }
        MinestomMenuSession session = prompt.session();
        if (sessions.get(viewerId) != session) {
            return;
        }
        scheduleNextTick(MenuTrace.propagate(() -> {
            if (!prompts.remove(viewerId, prompt)) {
                return;
            }
            CallbackFence fence = callbackFence(session);
            try {
                if (sessions.get(viewerId) != session) {
                    return;
                }
                AbstractInventory openInventory = session.viewer().getOpenInventory();
                if (openInventory != null && openInventory != session.inventory()) {
                    abandonSession(session);
                    return;
                }
                fence = callbackFence(session);
                List<ReactiveMenuEffect> effects = MenuTrace.time(
                        "runtime.reactiveDispatch",
                        () -> session.userCallback(() -> session.state().dispatchReactive(input)));
                if (!callbackCurrent(session, fence)) {
                    return;
                }
                boolean handled = MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, effects));
                if (!handled) {
                    MenuTrace.field("promptOutcome", "state-only");
                }
                if (sessions.get(viewerId) != session || prompts.containsKey(viewerId)) {
                    return;
                }
                AbstractInventory outcomeInventory = session.viewer().getOpenInventory();
                if (outcomeInventory != null && outcomeInventory != session.inventory()) {
                    abandonSession(session);
                    return;
                }
                boolean reopened = MenuTrace.time("session.reopenCurrentView", session::reopenCurrentView);
                if (!reopened) {
                    abandonSession(session);
                }
            } catch (RuntimeException exception) {
                if (callbackOwnerCurrent(session, fence)) {
                    quarantineCustodySession(session);
                }
            }
        }));
    }

    private static boolean isShiftClick(Click click) {
        return click instanceof Click.LeftShift || click instanceof Click.RightShift;
    }

    private static MenuClick toCompiledMenuClick(Click click) {
        if (click instanceof Click.Left || click instanceof Click.LeftShift) {
            return MenuClick.LEFT;
        }
        if (click instanceof Click.Right || click instanceof Click.RightShift) {
            return MenuClick.RIGHT;
        }
        return null;
    }

    private void abandonSession(MinestomMenuSession session) {
        prompts.remove(session.viewer().getUuid());
        retireInventory(session.viewer(), session.inventory());
        if (sessions.remove(session.viewer().getUuid(), session)) {
            MenuTrace.time("runtime.sessionDetach", session::detach);
        }
    }

    void abandonFailedTransition(MinestomMenuSession session, Inventory attemptedInventory) {
        retireInventory(session.viewer(), attemptedInventory);
        abandonSession(session);
        if (session.viewer().getOpenInventory() == attemptedInventory) {
            session.viewer().closeInventory();
        }
    }

    private static MenuClick toReactiveMenuClick(Click click) {
        if (click instanceof Click.Left || click instanceof Click.LeftShift) {
            return MenuClick.LEFT;
        }
        if (click instanceof Click.Right || click instanceof Click.RightShift) {
            return MenuClick.RIGHT;
        }
        return null;
    }

    private static MenuTickHandle scheduleOnServerTick(Runnable action) {
        var task = MinecraftServer.getSchedulerManager().scheduleTask(
                Objects.requireNonNull(action, "action"),
                TaskSchedule.tick(1),
                TaskSchedule.stop());
        return task::cancel;
    }

    private void recordSuppressedInput(String path, int slot, MenuClick button, String reason) {
        MenuTrace.incrementCount("suppressedInputs");
        MenuTrace.incrementCount("suppressedTickCapInputs");
        MenuTrace.field("inputPath", path);
        MenuTrace.field("suppressedReason", reason);
        if (slot >= 0) {
            MenuTrace.field("slot", slot);
        }
        if (button != null) {
            MenuTrace.field("button", button);
        }
    }

    void retireInventory(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        ArrayDeque<Inventory> retired = retiredInventories.computeIfAbsent(
                player.getUuid(),
                ignored -> new ArrayDeque<>());
        synchronized (retired) {
            if (retired.stream().noneMatch(candidate -> candidate == inventory)) {
                retired.addLast(inventory);
            }
            while (retired.size() > MAX_RETIRED_INVENTORIES_PER_VIEWER) {
                retired.removeFirst();
            }
        }
    }

    private boolean isRetiredInventory(UUID viewerId, Inventory inventory) {
        ArrayDeque<Inventory> retired = retiredInventories.get(viewerId);
        if (retired == null) {
            return false;
        }
        synchronized (retired) {
            return retired.stream().anyMatch(candidate -> candidate == inventory);
        }
    }

    private static MenuStack toMenuStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.isAir()) {
            return null;
        }
        Material material = itemStack.material();
        if (material == Material.AIR) {
            return null;
        }
        MenuStack.Builder builder = MenuStack.builder(MinestomMenuIcons.fromItemStack(itemStack))
                .amount(Math.max(1, itemStack.amount()));
        Component name = itemStack.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            builder.name(name);
        } else {
            builder.name(fallbackName(material));
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
        MenuTrace.withTrace(traceController, traceSink, "minestom", player.getUuid(), cause, action);
    }

    private static String cause(Click click) {
        if (click instanceof Click.LeftDrag || click instanceof Click.RightDrag) {
            return "drag";
        }
        if (click instanceof Click.LeftDropCursor || click instanceof Click.RightDropCursor) {
            return "drop-cursor";
        }
        return "click";
    }

    private static String flatten(Component component) {
        return PLAIN_TEXT.serialize(component);
    }

    record CallbackFence(
            MinestomMenuSession mappedSession,
            Inventory inventory,
            AbstractInventory openInventory,
            MenuCustodyLedger<ItemStack> custodyLedger,
            long actionVersion,
            long inventoryGeneration,
            long lifecycleGeneration
    ) {
    }

    private static final class NativeBeforeImages {

        private final MinestomMenuSession session;
        private final PlayerInventory viewerInventory;
        private final Inventory topInventory;
        private final int releasedSlot;
        private final Map<Integer, ItemStack> viewerSlots = new LinkedHashMap<>();
        private final Map<Integer, ItemStack> topSlots = new LinkedHashMap<>();
        private final List<Runnable> rollbackActions = new ArrayList<>();
        private ItemStack cursor;
        private boolean cursorCaptured;

        private NativeBeforeImages(MinestomMenuSession session, int releasedSlot) {
            this.session = session;
            this.viewerInventory = session.viewer().getInventory();
            this.topInventory = session.inventory();
            this.releasedSlot = releasedSlot;
        }

        private void captureViewer(int slot) {
            viewerSlots.putIfAbsent(slot, viewerInventory.getItemStack(slot));
        }

        private void captureCursor() {
            if (!cursorCaptured) {
                cursor = viewerInventory.getCursorItem();
                cursorCaptured = true;
            }
        }

        private void captureTarget(String key) {
            Integer slot = session.custodyLedger().targetSlots().get(key);
            if (slot == null) {
                throw new IllegalArgumentException("Unknown custody target: " + key);
            }
            topSlots.putIfAbsent(slot, topInventory.getItemStack(slot));
        }

        private void captureSource(MenuCustodyLedger.Source source) {
            switch (source) {
                case MenuCustodyLedger.Source.Cursor ignored -> captureCursor();
                case MenuCustodyLedger.Source.Target target -> captureTarget(target.key());
            }
        }

        private void captureDestination(MenuCustodyLedger.Destination destination) {
            switch (destination) {
                case MenuCustodyLedger.Destination.Cursor ignored -> captureCursor();
                case MenuCustodyLedger.Destination.Target target -> captureTarget(target.key());
                case MenuCustodyLedger.Destination.Released ignored -> {
                    if (releasedSlot < 0) {
                        throw new IllegalStateException("Released custody has no viewer destination");
                    }
                    captureViewer(releasedSlot);
                }
            }
        }

        private boolean sourceMatches(MenuCustodyLedger.Source source, ItemStack expected) {
            return switch (source) {
                case MenuCustodyLedger.Source.Cursor ignored -> viewerInventory.getCursorItem().equals(expected);
                case MenuCustodyLedger.Source.Target target -> {
                    int slot = session.custodyLedger().targetSlots().get(target.key());
                    yield topInventory.getItemStack(slot).equals(expected);
                }
            };
        }

        private void clearViewer(int slot) {
            ItemStack before = Objects.requireNonNull(viewerSlots.get(slot), "viewer slot before-image");
            mutate(
                    () -> viewerInventory.setItemStack(slot, before),
                    () -> viewerInventory.setItemStack(slot, ItemStack.AIR));
        }

        private void clearSource(MenuCustodyLedger.Source source) {
            switch (source) {
                case MenuCustodyLedger.Source.Cursor ignored -> mutate(
                        () -> viewerInventory.setCursorItem(cursor),
                        () -> viewerInventory.setCursorItem(ItemStack.AIR));
                case MenuCustodyLedger.Source.Target target -> {
                    int slot = session.custodyLedger().targetSlots().get(target.key());
                    ItemStack before = Objects.requireNonNull(topSlots.get(slot), "target before-image");
                    mutate(
                            () -> topInventory.setItemStack(slot, before),
                            () -> topInventory.setItemStack(slot, ItemStack.AIR));
                }
            }
        }

        private void writeDestination(MenuCustodyLedger.Destination destination, ItemStack item) {
            switch (destination) {
                case MenuCustodyLedger.Destination.Cursor ignored -> mutate(
                        () -> viewerInventory.setCursorItem(cursor),
                        () -> viewerInventory.setCursorItem(item));
                case MenuCustodyLedger.Destination.Target target -> {
                    int slot = session.custodyLedger().targetSlots().get(target.key());
                    ItemStack before = Objects.requireNonNull(topSlots.get(slot), "target before-image");
                    mutate(
                            () -> topInventory.setItemStack(slot, before),
                            () -> topInventory.setItemStack(slot, item));
                }
                case MenuCustodyLedger.Destination.Released ignored -> {
                    ItemStack before = Objects.requireNonNull(
                            viewerSlots.get(releasedSlot),
                            "released slot before-image");
                    mutate(
                            () -> viewerInventory.setItemStack(releasedSlot, before),
                            () -> viewerInventory.setItemStack(releasedSlot, item));
                }
            }
        }

        private void mutate(Runnable rollback, Runnable mutation) {
            rollbackActions.add(rollback);
            mutation.run();
        }

        private boolean rollback() {
            for (int index = rollbackActions.size() - 1; index >= 0; index--) {
                try {
                    rollbackActions.get(index).run();
                } catch (RuntimeException exception) {
                    rollbackActions.clear();
                    return false;
                }
            }
            rollbackActions.clear();
            return true;
        }
    }

    private static final class PendingTextPrompt {

        private final MinestomMenuSession session;
        private final ReactiveTextPromptRequest request;
        private final ReactiveTextPromptMode mode;
        private volatile boolean awaitingMenuClose;

        private PendingTextPrompt(MinestomMenuSession session, ReactiveTextPromptRequest request,
                                  ReactiveTextPromptMode mode, boolean awaitingMenuClose) {
            this.session = session;
            this.request = request;
            this.mode = mode;
            this.awaitingMenuClose = awaitingMenuClose;
        }

        private MinestomMenuSession session() {
            return session;
        }

        private ReactiveTextPromptRequest request() {
            return request;
        }

        private ReactiveTextPromptMode mode() {
            return mode;
        }

        private boolean awaitingMenuClose() {
            return awaitingMenuClose;
        }

        private void awaitingMenuClose(boolean awaitingMenuClose) {
            this.awaitingMenuClose = awaitingMenuClose;
        }
    }
}
