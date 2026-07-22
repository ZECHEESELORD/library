package sh.harold.library.menu.fabric;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuCustodyDecision;
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
import sh.harold.library.menu.core.MenuTrace;
import sh.harold.library.menu.core.MenuCustodyLedger;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.MenuTickHandle;
import sh.harold.library.menu.core.MenuTickScheduler;
import sh.harold.library.sound.fabric.FabricServerSoundCuePlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class FabricMenuRuntime implements AutoCloseable {

    static final boolean PROMPT_CAN_CLOSE_WITH_ESCAPE = false;
    static final String PROMPT_COMMAND = "creative_library_menu_prompt";
    static final int STORAGE_SLOT_COUNT = 36;

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final String PROMPT_INPUT_KEY = "value";

    private final Map<UUID, FabricMenuSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTextPrompt> prompts = new ConcurrentHashMap<>();
    private final FabricMenuRenderer renderer;
    private final FabricServerSoundCuePlatform sounds;
    private final FabricMenuTaskScheduler tasks = new FabricMenuTaskScheduler();
    private final MenuTraceController traceController;
    private final Consumer<String> traceSink;

    FabricMenuRuntime(FabricMenuRenderer renderer, FabricServerSoundCuePlatform sounds,
                      MenuTraceController traceController, Consumer<String> traceSink) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.traceController = Objects.requireNonNull(traceController, "traceController");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
    }

    void open(ServerPlayer player, MenuDefinition menu) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        FabricMenuSession callbackSession = sessions.get(player.getUUID());
        if (callbackSession != null && callbackSession.deferLifecycle(() -> {
            if (active(callbackSession)) {
                open(player, menu);
            }
        })) {
            return;
        }
        FabricMenuSession[] opening = new FabricMenuSession[1];
        try {
            trace(player, "open", () -> {
                FabricMenuSession previous = sessions.get(player.getUUID());
                if (previous != null) {
                    removePrompt(previous);
                    try {
                        MenuTrace.time("runtime.detachPrevious", () ->
                                terminate(previous, MenuCustodyGesture.SettleReason.NAVIGATE, true));
                    } finally {
                        sessions.remove(player.getUUID(), previous);
                    }
                }
                FabricMenuSession session = MenuTrace.time("runtime.createSession",
                        () -> new FabricMenuSession(this, player, new MenuSessionState(menu)));
                opening[0] = session;
                MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
                sessions.put(player.getUUID(), session);
                MenuTrace.time("runtime.show", () -> show(session, true));
            });
        } catch (RuntimeException exception) {
            FabricMenuSession failed = opening[0];
            if (failed != null) {
                quarantine(failed);
            }
        }
    }

    void onTopClick(FabricMenuContainer container, ServerPlayer player, int slot, MenuClick button, boolean shift) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container)) {
            return;
        }
        if (session.state().reactive()) {
            traceInteraction(session, player, "click", () -> {
                MenuTrace.field("path", "reactive");
                MenuTrace.field("slot", slot);
                MenuTrace.field("button", button);
                traceRenderedTitle(session);
                handleReactiveTopClick(session, player, slot, button, shift, container);
            });
            return;
        }
        if (shift) {
            return;
        }
        MenuInteraction interaction;
        long callbackGeneration = session.callbackGeneration();
        try {
            interaction = session.invokeUserCallback(
                    () -> session.state().interaction(slot, button).orElse(null));
        } catch (RuntimeException exception) {
            quarantine(session);
            return;
        }
        if (!activeAfterCallback(session, callbackGeneration)
                || !liveContainer(session, player, container)) {
            return;
        }
        if (interaction == null) {
            return;
        }
        traceInteraction(session, player, "click", () -> {
            MenuTrace.field("path", "compiled");
            MenuTrace.field("slot", slot);
            MenuTrace.field("button", button);
            traceRenderedTitle(session);
            if (!allowInput(session, new CompiledClickInput(slot, button))) {
                return;
            }
            handleDirectInteraction(session, player, button, interaction);
        });
    }

    void onBottomClick(FabricMenuContainer container, ServerPlayer player, int bottomSlot, MenuClick button,
                       boolean shift, ItemStack clickedItem) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container)
                || !session.state().reactive() || !session.custodyEnabled()) {
            return;
        }
        traceInteraction(session, player, "click", () -> {
            MenuTrace.field("path", "reactive");
            MenuTrace.field("slot", bottomSlot);
            MenuTrace.field("button", button);
            traceRenderedTitle(session);
            if (!allowInput(session, new ReactiveInventoryClickInput(bottomSlot, button, shift))) {
                return;
            }
            ItemStack observed = copyItem(clickedItem);
            MenuViewerSlot viewerSlot = new MenuViewerSlot(
                    session.nextCustodyObservationId(), bottomSlot, toMenuStack(observed));
            handleCustodyGesture(session, player, container,
                    new MenuCustodyGesture.ViewerClick(viewerSlot, button, shift), observed, true);
        });
    }

    void onDrag(FabricMenuContainer container, ServerPlayer player, MenuClick button, List<Integer> slots) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container)
                || !session.state().reactive() || !session.custodyEnabled()) {
            return;
        }
        traceInteraction(session, player, "drag", () -> {
            MenuTrace.field("path", "reactive");
            MenuTrace.field("button", button);
            MenuTrace.setCount("dragSlots", slots.size());
            traceRenderedTitle(session);
            if (!allowInput(session, new ReactiveDragInput(button, slots))) {
                return;
            }
            List<String> targets = new ArrayList<>();
            boolean targetsOnly = true;
            for (int slot : slots) {
                String target = session.state().custodyTargetAt(slot).orElse(null);
                if (target == null) {
                    targetsOnly = false;
                } else if (!targets.contains(target)) {
                    targets.add(target);
                }
            }
            if (targets.isEmpty()) {
                return;
            }
            handleCustodyGesture(session, player, container,
                    new MenuCustodyGesture.TargetDrag(targets, button), null, targetsOnly);
        });
    }

    void onDropCursor(FabricMenuContainer container, ServerPlayer player, MenuClick button) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container)
                || !session.state().reactive() || !session.custodyEnabled()) {
            return;
        }
        traceInteraction(session, player, "drop-cursor", () -> {
            MenuTrace.field("path", "reactive");
            MenuTrace.field("button", button);
            traceRenderedTitle(session);
            if (!allowInput(session, new ReactiveDropCursorInput(button))) {
                return;
            }
            handleCustodyGesture(session, player, container,
                    new MenuCustodyGesture.OutsideClick(button), null, true);
        });
    }

    void beforeContainerRemoved(FabricMenuContainer container, ServerPlayer player) {
        FabricMenuSession session = container.session();
        if (container.transitionClosing() || container.nativeDriftClosing() || !session.custodyEnabled()) {
            return;
        }
        MenuCustodyGesture.SettleReason reason = container.promptClosing()
                ? MenuCustodyGesture.SettleReason.PROMPT
                : MenuCustodyGesture.SettleReason.CLOSE;
        if (!settleCustody(session, container, reason) && container.promptClosing()) {
            sessions.remove(player.getUUID(), session);
            removePrompt(session);
            session.detach();
            container.markTransitionClose();
        }
    }

    void onContainerRemoved(FabricMenuContainer container, ServerPlayer player) {
        FabricMenuSession session = container.session();
        session.clearContainer(container);
        if (container.transitionClosing() || container.nativeDriftClosing()) {
            return;
        }
        try {
            trace(player, "close", () -> {
                traceRenderedTitle(session);
                if (advancePromptAfterClose(player.getUUID(), session, container)) {
                    return;
                }
                if (sessions.remove(player.getUUID(), session)) {
                    MenuTrace.time("runtime.sessionDetach", session::detach);
                }
            });
        } catch (RuntimeException exception) {
            quarantine(session);
        }
    }

    boolean handleChatMessage(ServerPlayer player, String message) {
        PendingTextPrompt prompt = prompts.get(player.getUUID());
        if (prompt == null || prompt.session() != sessions.get(player.getUUID())
                || prompt.mode() != ReactiveTextPromptMode.CHAT
                || prompt.phase() != PendingTextPromptPhase.ACTIVE) {
            return false;
        }
        String submitted = message == null ? "" : message;
        scheduleNextTick(MenuTrace.propagate(() -> completePrompt(prompt,
                "cancel".equalsIgnoreCase(submitted.trim())
                        ? new ReactiveMenuInput.TextPromptCancelled(prompt.request().key(), ReactiveTextPromptMode.CHAT)
                        : new ReactiveMenuInput.TextPromptSubmitted(prompt.request().key(), submitted, ReactiveTextPromptMode.CHAT))));
        return true;
    }

    boolean handlePromptCommand(ServerPlayer player, String token, String value, boolean cancelled) {
        PendingTextPrompt prompt = prompts.get(player.getUUID());
        if (prompt == null || prompt.session() != sessions.get(player.getUUID())
                || prompt.mode() != ReactiveTextPromptMode.PROMPT
                || prompt.phase() != PendingTextPromptPhase.ACTIVE
                || !prompt.token().equals(token)) {
            return false;
        }
        scheduleNextTick(MenuTrace.propagate(() -> completePrompt(prompt,
                cancelled
                        ? new ReactiveMenuInput.TextPromptCancelled(prompt.request().key(), ReactiveTextPromptMode.PROMPT)
                        : new ReactiveMenuInput.TextPromptSubmitted(prompt.request().key(), value == null ? "" : value, ReactiveTextPromptMode.PROMPT))));
        return true;
    }

    void onPlayerDisconnect(ServerPlayer player) {
        prompts.remove(player.getUUID());
        FabricMenuSession session = sessions.get(player.getUUID());
        if (session != null) {
            try {
                terminate(session, MenuCustodyGesture.SettleReason.DISCONNECT, false);
            } finally {
                sessions.remove(player.getUUID(), session);
            }
        }
    }

    void onPlayerAllowDeath(ServerPlayer player) {
        FabricMenuSession session = sessions.get(player.getUUID());
        if (session == null) {
            return;
        }
        long attempt = session.beginDeathAttempt();
        if (attempt == 0L) {
            return;
        }
        try {
            boolean settled = settleCustodyBeforeDeath(
                    () -> !session.custodyEnabled() || settleCustody(
                            session, session.container(), MenuCustodyGesture.SettleReason.DEATH),
                    () -> {
                        if (active(session)) {
                            quarantine(session);
                        }
                    });
            if (!settled || !active(session)) {
                return;
            }
            scheduleNextTick(() -> reconcileCancelledDeath(session, attempt));
        } catch (RuntimeException exception) {
            session.consumeDeathAttempt(attempt);
            if (active(session)) {
                try {
                    quarantine(session);
                } catch (RuntimeException ignored) {
                    // Death must not be cancelled by menu cleanup failure.
                }
            }
        }
    }

    void onPlayerDeath(ServerPlayer player) {
        prompts.remove(player.getUUID());
        FabricMenuSession session = sessions.remove(player.getUUID());
        if (session == null) {
            return;
        }
        FabricMenuContainer container = session.container();
        if (container != null) {
            container.markTransitionClose();
        }
        try {
            session.detach();
        } catch (RuntimeException ignored) {
            // The session is already unmapped; death processing must continue.
        }
    }

    private void reconcileCancelledDeath(FabricMenuSession session, long attempt) {
        if (!active(session) || !session.consumeDeathAttempt(attempt)) {
            return;
        }
        try {
            session.restoreSettledCustodyView();
        } catch (RuntimeException exception) {
            if (active(session)) {
                quarantine(session);
            }
        }
    }

    void onServerTick() {
        tasks.tick();
    }

    void onTick(FabricMenuSession session) {
        if (sessions.get(session.viewer().getUUID()) != session || promptBlocksInventoryOpen(session)) {
            return;
        }
        try {
            trace(session.viewer(), "tick", () -> {
                MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
                traceRenderedTitle(session);
                long beforeRevision = session.state().revision();
                long callbackGeneration = session.callbackGeneration();
                List<ReactiveMenuEffect> effects = MenuTrace.time(
                        "runtime.stateTick", () -> session.invokeUserCallback(session.state()::tick));
                if (!activeAfterCallback(session, callbackGeneration)) {
                    return;
                }
                if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, session.viewer(), effects))) {
                    if (activeAfterCallback(session, callbackGeneration)) {
                        renderIfStateChanged(beforeRevision, session.state().revision(), session::renderCurrentView);
                    }
                }
            });
        } catch (RuntimeException exception) {
            quarantine(session);
        }
    }

    void refresh(FabricMenuSession session) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> refresh(session))) {
            return;
        }
        try {
            session.state().invalidateView();
            MenuTrace.time("session.renderCurrentView", () -> session.renderCurrentView());
        } catch (RuntimeException exception) {
            quarantine(session);
        }
    }

    void replace(FabricMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> replace(session, menu))) {
            return;
        }
        MenuTrace.time("runtime.replace", () -> transition(session,
                () -> session.state().prepareOpenChild(menu)));
    }

    void replaceCurrent(FabricMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> replaceCurrent(session, menu))) {
            return;
        }
        MenuTrace.time("runtime.replaceCurrent", () -> transition(session,
                () -> Optional.of(session.state().prepareReplaceCurrent(menu))));
    }

    void back(FabricMenuSession session) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> back(session))) {
            return;
        }
        MenuTrace.time("runtime.back", () -> transition(session, session.state()::prepareBack));
    }

    void close(FabricMenuSession session) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        if (session.deferLifecycle(() -> close(session))) {
            return;
        }
        removePrompt(session);
        try {
            MenuTrace.time("runtime.close", () ->
                    terminate(session, MenuCustodyGesture.SettleReason.CLOSE, true));
        } finally {
            sessions.remove(session.viewer().getUUID(), session);
        }
    }

    boolean render(FabricMenuSession session, SimpleContainer container,
                   List<MenuSlot> previousSlots, List<MenuSlot> nextSlots,
                   net.minecraft.core.HolderLookup.Provider registries) {
        long started = System.nanoTime();
        int changedSlots = 0;
        for (int slot = 0; slot < nextSlots.size(); slot++) {
            MenuSlot nextSlot = nextSlots.get(slot);
            if (previousSlots == null || !nextSlot.equals(previousSlots.get(slot))) {
                String custodyTarget = session.custody().ledger().targetAt(slot).orElse(null);
                if (custodyTarget != null && session.custody().ledger().target(custodyTarget).isPresent()) {
                    continue;
                }
                changedSlots++;
                int renderedSlot = slot;
                long renderStarted = System.nanoTime();
                ItemStack rendered = renderer.render(nextSlot, registries);
                long renderElapsed = System.nanoTime() - renderStarted;
                MenuTrace.addDuration("runtime.slotRender", renderElapsed);
                MenuTrace.detailIfSlow("slot-render", renderElapsed,
                        () -> "slot=" + renderedSlot + " title=" + flatten(nextSlot.title()));

                long patchStarted = System.nanoTime();
                container.setItem(renderedSlot, rendered);
                long patchElapsed = System.nanoTime() - patchStarted;
                MenuTrace.addDuration("runtime.slotPatch", patchElapsed);
                MenuTrace.detailIfSlow("slot-patch", patchElapsed,
                        () -> "slot=" + renderedSlot + " title=" + flatten(nextSlot.title()));
            }
        }
        MenuTrace.setCount("changedSlots", changedSlots);
        MenuTrace.addDuration("runtime.inventoryPatch", System.nanoTime() - started);
        return changedSlots > 0;
    }

    boolean renderCustody(FabricMenuSession session, SimpleContainer container, List<MenuSlot> previousSlots,
                          List<MenuSlot> nextSlots, net.minecraft.core.HolderLookup.Provider registries) {
        if (!session.custodyEnabled()) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, Integer> target : session.custody().ledger().targetSlots().entrySet()) {
            String key = target.getKey();
            int slot = target.getValue();
            ItemStack base = session.custodyBaseItem(key);
            if (base == null || previousSlots == null || !nextSlots.get(slot).equals(previousSlots.get(slot))) {
                base = renderer.render(nextSlots.get(slot), registries);
                session.cacheCustodyBaseItem(key, base);
            }
            MenuCustodyLedger.Entry<ItemStack> entry =
                    session.custody().ledger().target(key).orElse(null);
            if (entry != null) {
                if (!ItemStack.matches(container.getItem(slot), entry.nativeItem())) {
                    MenuTrace.field("custodySession", "native-drift");
                    quarantineNativeDrift(session);
                    return false;
                }
                continue;
            }
            ItemStack expected = base.copy();
            if (!ItemStack.matches(container.getItem(slot), expected)) {
                container.setItem(slot, expected);
                changed = true;
            }
        }
        return changed;
    }

    boolean syncCustodyCursor(FabricMenuSession session, FabricMenuContainer container) {
        if (!session.custodyEnabled() || container == null) {
            return false;
        }
        ItemStack expected = session.custody().ledger().cursor()
                .map(MenuCustodyLedger.Entry::nativeItem)
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
        if (ItemStack.matches(container.getCarried(), expected)) {
            return false;
        }
        MenuTrace.field("custodySession", "native-drift");
        quarantineNativeDrift(session);
        return false;
    }

    boolean validateCustodyView(FabricMenuSession session, FabricMenuContainer container) {
        if (!session.custodyEnabled()) {
            return true;
        }
        if (container != null && session.custody().nativeViewReady(
                new FabricNativeAccess(session, container, session.viewer()))) {
            return true;
        }
        MenuTrace.field("custodySession", "native-drift");
        quarantineNativeDrift(session);
        return false;
    }

    boolean active(FabricMenuSession session) {
        return sessions.get(session.viewer().getUUID()) == session;
    }

    boolean activeAfterCallback(FabricMenuSession session, long expectedGeneration) {
        return active(session) && session.callbackGenerationUnchanged(expectedGeneration);
    }

    MenuTickScheduler tickScheduler() {
        return tasks.intervalScheduler();
    }

    MenuTickHandle scheduleNextTick(Runnable action) {
        return tasks.scheduleNextTick(Objects.requireNonNull(action, "action"));
    }

    @Override
    public void close() {
        new ArrayList<>(sessions.values()).forEach(session -> {
            if (active(session)) {
                try {
                    terminate(session, MenuCustodyGesture.SettleReason.SHUTDOWN, true);
                } finally {
                    sessions.remove(session.viewer().getUUID(), session);
                }
            }
        });
        sessions.clear();
        prompts.clear();
    }

    private void handleReactiveTopClick(FabricMenuSession session, ServerPlayer player, int slot, MenuClick button,
                                        boolean shift, FabricMenuContainer container) {
        long callbackGeneration = session.callbackGeneration();
        ReactiveClickRoute route = session.invokeUserCallback(() -> {
            String custodyTarget = session.custodyEnabled()
                    ? session.state().custodyTargetAt(slot).orElse(null)
                    : null;
            MenuInteraction interaction = custodyTarget == null
                    ? session.state().interaction(slot, button).orElse(null)
                    : null;
            boolean acceptsReactiveClick = interaction != null || session.state().acceptsReactiveClick(slot);
            return new ReactiveClickRoute(custodyTarget, interaction, acceptsReactiveClick);
        });
        if (!activeAfterCallback(session, callbackGeneration)
                || !liveContainer(session, player, container)) {
            return;
        }
        if (route.custodyTarget() != null) {
            if (!allowInput(session, new ReactiveTopClickInput(slot, button, shift))) {
                return;
            }
            handleCustodyGesture(session, player, container,
                    new MenuCustodyGesture.TargetClick(route.custodyTarget(), button, shift), null, true);
            return;
        }
        MenuInteraction interaction = route.interaction();
        if (interaction != null && !(interaction.action() instanceof MenuSlotAction.Dispatch)) {
            if (!allowInput(session, new ReactiveTopClickInput(slot, button, shift))) {
                return;
            }
            handleDirectInteraction(session, player, button, interaction);
            return;
        }
        if (!route.acceptsReactiveClick()) {
            return;
        }
        if (!allowInput(session, new ReactiveTopClickInput(slot, button, shift))) {
            return;
        }
        Object message = interaction != null ? ((MenuSlotAction.Dispatch) interaction.action()).message() : null;
        handleReactiveInput(session, player, new ReactiveMenuInput.Click(slot, button, shift, message), interaction);
    }

    private void handleReactiveInput(FabricMenuSession session, ServerPlayer player, ReactiveMenuInput input, MenuInteraction interaction) {
        try {
            if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
                playInteractionSound(player, interaction);
            }
            long beforeRevision = session.state().revision();
            long callbackGeneration = session.callbackGeneration();
            List<ReactiveMenuEffect> effects = MenuTrace.time(
                    "runtime.reactiveDispatch",
                    () -> session.invokeUserCallback(() -> session.state().dispatchReactive(input)));
            if (!activeAfterCallback(session, callbackGeneration)) {
                return;
            }
            if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects))
                    && activeAfterCallback(session, callbackGeneration)) {
                renderIfStateChanged(beforeRevision, session.state().revision(), session::renderCurrentView);
            }
        } catch (RuntimeException exception) {
            quarantine(session);
        }
    }

    private void handleCustodyGesture(FabricMenuSession session, ServerPlayer player,
                                      FabricMenuContainer container, MenuCustodyGesture gesture,
                                      ItemStack observedItem, boolean structurallySupported) {
        FabricMenuCustody.Outcome outcome;
        if (!structurallySupported) {
            outcome = session.custody().rejectGesture(gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
        } else {
            CustodyCallbackFence fence = new CustodyCallbackFence(
                    container,
                    session.custody(),
                    session.callbackGeneration(),
                    session.actionVersion(),
                    session.state().revision());
            MenuCustodyDecision decision;
            try {
                decision = Objects.requireNonNull(
                        session.invokeUserCallback(() -> session.state().decideCustody(
                                gesture, fence.custody().ledger().snapshot())),
                        "custody policy decision");
            } catch (RuntimeException exception) {
                if (!custodyCallbackStillCurrent(session, player, fence)) {
                    return;
                }
                outcome = fence.custody().rejectGesture(gesture, MenuCustodyFailure.POLICY_REJECTED);
                dispatchCustodyOutcome(session, player, container, outcome);
                return;
            }
            if (!custodyCallbackStillCurrent(session, player, fence)) {
                return;
            }
            outcome = fence.custody().transact(
                    gesture, decision, observedItem, new FabricNativeAccess(session, container, player));
        }
        dispatchCustodyOutcome(session, player, container, outcome);
    }

    private void dispatchCustodyOutcome(FabricMenuSession session, ServerPlayer player,
                                        FabricMenuContainer container, FabricMenuCustody.Outcome outcome) {
        if (outcome.failure() == MenuCustodyFailure.NATIVE_MUTATION_FAILED) {
            quarantineNativeDrift(session);
            return;
        }
        try {
            dispatchCustodyOutcomeUnchecked(session, player, container, outcome);
        } catch (RuntimeException exception) {
            quarantine(session);
        }
    }

    private void dispatchCustodyOutcomeUnchecked(FabricMenuSession session, ServerPlayer player,
                                                 FabricMenuContainer container,
                                                 FabricMenuCustody.Outcome outcome) {
        ReactiveMenuInput input = outcome.committed()
                ? new ReactiveMenuInput.CustodyCommitted(
                        outcome.operationId(), outcome.gesture(), outcome.snapshot())
                : new ReactiveMenuInput.CustodyRejected(
                        outcome.operationId(), outcome.gesture(), outcome.failure(), outcome.snapshot());
        long beforeRevision = session.state().revision();
        long callbackGeneration = session.callbackGeneration();
        List<ReactiveMenuEffect> effects = MenuTrace.time(
                "runtime.custodyDispatch",
                () -> session.invokeUserCallback(() -> session.state().dispatchReactive(input)));
        if (!activeAfterCallback(session, callbackGeneration)
                || !liveContainer(session, player, container)) {
            return;
        }
        if (MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects))) {
            return;
        }
        if (!activeAfterCallback(session, callbackGeneration)
                || !liveContainer(session, player, container)) {
            return;
        }
        if (beforeRevision != session.state().revision()) {
            MenuTrace.time("session.renderCurrentView", () -> session.renderCurrentView(outcome.committed()));
            return;
        }
        if (outcome.committed()
                && sessions.get(player.getUUID()) == session
                && session.matches(player, container)) {
            renderCustody(session, container.topContainer(), session.renderedSlots(), session.renderedSlots(),
                    player.level().registryAccess());
            if (!active(session)) {
                return;
            }
            syncCustodyCursor(session, container);
            if (!active(session)) {
                return;
            }
            container.broadcastChanges();
        }
    }

    boolean settleForRebuild(FabricMenuSession session) {
        if (!session.custodyEnabled()) {
            return true;
        }
        long callbackGeneration = session.callbackGeneration();
        boolean settled = settleCustody(
                session, session.container(), MenuCustodyGesture.SettleReason.NAVIGATE);
        if (!settled && activeAfterCallback(session, callbackGeneration)) {
            quarantine(session);
        }
        return settled && activeAfterCallback(session, callbackGeneration);
    }

    private void transition(
            FabricMenuSession session,
            Supplier<Optional<MenuSessionState.PreparedTransition>> prepare
    ) {
        FabricMenuCustody guard = session.custody();
        if (!guard.beginTransition()) {
            return;
        }
        FabricMenuContainer originalContainer = session.container();
        boolean restoreSettledView = false;
        long callbackGeneration = session.callbackGeneration();
        try {
            if (guard.enabled() && !drainCustodyIfHeld(guard, () -> drainCustody(
                    session, session.container(), MenuCustodyGesture.SettleReason.NAVIGATE, guard))) {
                if (activeAfterCallback(session, callbackGeneration)) {
                    quarantine(session);
                }
                return;
            }
            if (!activeAfterCallback(session, callbackGeneration)) {
                return;
            }
            Optional<MenuSessionState.PreparedTransition> prepared =
                    session.invokeUserCallback(prepare);
            if (!activeAfterCallback(session, callbackGeneration)) {
                return;
            }
            if (prepared.isEmpty()) {
                restoreSettledView = true;
            } else if (session.applyTransition(prepared.orElseThrow())) {
                show(session, true);
            } else {
                restoreSettledView = true;
            }
        } catch (RuntimeException exception) {
            restoreSettledView = true;
        } finally {
            guard.endTransition();
        }
        if (restoreSettledView) {
            restoreFailedTransition(
                    originalContainer != null
                            && liveContainer(session, session.viewer(), originalContainer),
                    session::restoreSettledCustodyView,
                    () -> quarantine(session));
        }
    }

    private boolean settleCustody(FabricMenuSession session, FabricMenuContainer container,
                                  MenuCustodyGesture.SettleReason reason) {
        FabricMenuCustody guard = session.custody();
        if (!guard.beginTransition()) {
            return false;
        }
        try {
            return drainCustodyIfHeld(guard, () -> drainCustody(session, container, reason, guard));
        } finally {
            guard.endTransition();
        }
    }

    private boolean drainCustody(FabricMenuSession session, FabricMenuContainer container,
                                 MenuCustodyGesture.SettleReason reason, FabricMenuCustody guard) {
        FabricMenuCustody.Outcome outcome;
        if (container == null && !guard.empty()) {
            outcome = guard.rejectGesture(
                    new MenuCustodyGesture.Settle(reason), MenuCustodyFailure.NATIVE_MUTATION_FAILED);
        } else {
            try {
                outcome = guard.settle(
                        reason, new FabricNativeAccess(session, container, session.viewer())).getFirst();
            } catch (RuntimeException exception) {
                outcome = guard.rejectGesture(
                        new MenuCustodyGesture.Settle(reason), MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            }
        }
        ReactiveMenuInput input = outcome.committed()
                ? new ReactiveMenuInput.CustodyCommitted(
                        outcome.operationId(), outcome.gesture(), outcome.snapshot())
                : new ReactiveMenuInput.CustodyRejected(
                        outcome.operationId(), outcome.gesture(), outcome.failure(), outcome.snapshot());
        try {
            session.invokeUserCallback(() -> session.state().dispatchReactive(input));
        } catch (RuntimeException exception) {
            return false;
        }
        if (outcome.committed()) {
            session.markSettledCustodyViewDirty();
        }
        return outcome.committed() && outcome.snapshot().empty() && guard.empty();
    }

    private void terminate(FabricMenuSession session, MenuCustodyGesture.SettleReason reason,
                           boolean closeNativeContainer) {
        if (session.custodyEnabled()) {
            settleCustody(session, session.container(), reason);
        }
        FabricMenuContainer container = session.container();
        session.detach();
        if (closeNativeContainer && container != null && session.viewer().containerMenu == container) {
            container.markTransitionClose();
            session.viewer().closeContainer();
        }
    }

    private void quarantine(FabricMenuSession session) {
        sessions.remove(session.viewer().getUUID(), session);
        removePrompt(session);
        FabricMenuContainer container = session.container();
        if (session.custodyEnabled() && container != null) {
            try {
                session.custody().settle(
                        MenuCustodyGesture.SettleReason.CLOSE,
                        new FabricNativeAccess(session, container, session.viewer()));
            } catch (RuntimeException ignored) {
                // Closing abandons unresolved ledger entries rather than minting another native copy.
            }
        }
        session.detach();
        if (container != null && session.viewer().containerMenu == container) {
            container.markTransitionClose();
            session.viewer().closeContainer();
        }
    }

    private void quarantineNativeDrift(FabricMenuSession session) {
        sessions.remove(session.viewer().getUUID(), session);
        removePrompt(session);
        FabricMenuContainer container = session.container();
        session.detach();
        if (container != null && session.viewer().containerMenu == container) {
            container.markNativeDriftClose();
            session.viewer().closeContainer();
        }
    }

    static boolean renderIfStateChanged(long beforeRevision, long afterRevision, Runnable render) {
        Objects.requireNonNull(render, "render");
        if (beforeRevision == afterRevision) {
            return false;
        }
        MenuTrace.time("session.renderCurrentView", render);
        return true;
    }

    static boolean drainCustodyIfHeld(FabricMenuCustody guard, BooleanSupplier drain) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(drain, "drain");
        return guard.empty() || drain.getAsBoolean();
    }

    static boolean settleCustodyBeforeDeath(BooleanSupplier settle, Runnable quarantine) {
        Objects.requireNonNull(settle, "settle");
        Objects.requireNonNull(quarantine, "quarantine");
        try {
            if (settle.getAsBoolean()) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Quarantine below retains the no-dupe-first failure policy.
        }
        try {
            quarantine.run();
        } catch (RuntimeException ignored) {
            // Death must remain allowed even if cleanup itself fails.
        }
        return false;
    }

    static boolean restoreFailedTransition(
            boolean originalContainerStillValid,
            BooleanSupplier restore,
            Runnable quarantine
    ) {
        Objects.requireNonNull(restore, "restore");
        Objects.requireNonNull(quarantine, "quarantine");
        if (!originalContainerStillValid) {
            try {
                quarantine.run();
            } catch (RuntimeException ignored) {
                // The failed transition is already being abandoned.
            }
            return false;
        }
        try {
            return restore.getAsBoolean();
        } catch (RuntimeException exception) {
            try {
                quarantine.run();
            } catch (RuntimeException ignored) {
                // The failed transition is already being abandoned.
            }
            return false;
        }
    }

    private void handleDirectInteraction(FabricMenuSession session, ServerPlayer player, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(player, interaction);
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> transition(
                    session, () -> session.state().prepareOpenFrame(openFrame.frameId()));
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                long before = session.actionVersion();
                long callbackGeneration = session.callbackGeneration();
                MenuContext context = new MenuContext(click, session.state().frameId(), session.state().values(), session);
                session.invokeUserCallback(() -> execute.action().execute(context));
                if (activeAfterCallback(session, callbackGeneration) && session.actionVersion() == before) {
                    session.renderCurrentView();
                }
            }
            case MenuSlotAction.Dispatch ignored -> {
            }
        }
    }

    private boolean applyEffects(FabricMenuSession session, ServerPlayer player, List<ReactiveMenuEffect> effects) {
        for (ReactiveMenuEffect effect : effects) {
            if (!active(session)) {
                return true;
            }
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

    FabricMenuContainer openMenu(FabricMenuSession session, ServerPlayer player, int rows, Component title, SimpleContainer topContainer) {
        if (!active(session) || session.viewer() != player || promptBlocksInventoryOpen(session)) {
            return null;
        }
        FabricMenuContainer current = session.container();
        if (current != null && player.containerMenu == current) {
            current.markTransitionClose();
        }
        MenuProvider provider = new SimpleMenuProvider((containerId, inventory, openingPlayer) -> {
            FabricMenuContainer created = new FabricMenuContainer(this, session, containerId, inventory, topContainer, rows);
            session.attachContainer(created);
            return created;
        }, FabricMenuComponents.toNative(title, player.level().registryAccess()));
        player.openMenu(provider);
        FabricMenuContainer opened = session.container();
        if (!active(session)) {
            if (opened != null && player.containerMenu == opened) {
                opened.markTransitionClose();
                player.closeContainer();
            }
            return null;
        }
        if (opened == null || opened == current || opened.closed()) {
            if (current != null && !current.closed()) {
                current.clearTransitionClose();
            }
            return null;
        }
        return opened;
    }

    private void playInteractionSound(ServerPlayer player, MenuInteraction interaction) {
        if (interaction == null) {
            return;
        }
        Key soundCueKey = interaction.soundCueKey();
        if (soundCueKey != null) {
            sounds.play(player, soundCueKey);
        }
    }

    private void show(FabricMenuSession session, boolean activate) {
        if (!active(session)) {
            return;
        }
        if (activate) {
            long callbackGeneration = session.callbackGeneration();
            List<ReactiveMenuEffect> effects = MenuTrace.time(
                    "runtime.stateOpened", () -> session.invokeUserCallback(session.state()::opened));
            if (!activeAfterCallback(session, callbackGeneration)) {
                return;
            }
            if (MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, session.viewer(), effects))) {
                return;
            }
            if (!activeAfterCallback(session, callbackGeneration)) {
                return;
            }
        }
        MenuTrace.time("session.renderCurrentView", () -> session.renderCurrentView());
    }

    private void openTextPrompt(FabricMenuSession session, ServerPlayer player, ReactiveTextPromptRequest request) {
        if (!active(session)) {
            return;
        }
        PendingTextPrompt prompt = new PendingTextPrompt(
                session,
                request,
                resolvePromptMode(request),
                Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36),
                session.container(),
                PendingTextPromptPhase.AWAITING_MENU_CLOSE);
        prompts.put(player.getUUID(), prompt);
        session.suspendTickingForPrompt();
        if (closeViewerInventoryForPrompt(session, player, prompt)) {
            return;
        }
        activatePrompt(prompt, player);
    }

    private boolean closeViewerInventoryForPrompt(FabricMenuSession session, ServerPlayer player, PendingTextPrompt prompt) {
        FabricMenuContainer active = prompt.closingContainer();
        if (active == null || player.containerMenu != active) {
            return false;
        }
        active.markPromptClose();
        session.clearContainer(active);
        player.closeContainer();
        return true;
    }

    private boolean advancePromptAfterClose(UUID viewerId, FabricMenuSession session, FabricMenuContainer container) {
        PendingTextPrompt prompt = prompts.get(viewerId);
        if (prompt == null || prompt.session() != session
                || prompt.phase() != PendingTextPromptPhase.AWAITING_MENU_CLOSE
                || prompt.closingContainer() != container) {
            return false;
        }
        prompt.phase(PendingTextPromptPhase.ACTIVE);
        scheduleNextTick(MenuTrace.propagate(() -> runPromptActivationPhase(() -> {
            if (prompts.get(viewerId) == prompt && sessions.get(viewerId) == session) {
                activatePrompt(prompt, session.viewer());
            }
        }, () -> quarantine(session))));
        return true;
    }

    private void activatePrompt(PendingTextPrompt prompt, ServerPlayer player) {
        prompt.phase(PendingTextPromptPhase.ACTIVE);
        switch (prompt.mode()) {
            case PROMPT -> openDialogPrompt(prompt, player);
            case CHAT -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    prompt.request().prompt() + " Type your response in chat or send 'cancel' to keep the current value."));
            default -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    prompt.request().prompt() + " Type your response in chat or send 'cancel' to keep the current value."));
        }
    }

    private void openDialogPrompt(PendingTextPrompt prompt, ServerPlayer player) {
        ReactiveTextPromptRequest request = prompt.request();
        ParsedTemplate submitTemplate = parseTemplate("/" + PROMPT_COMMAND + " submit " + prompt.token() + " $(" + PROMPT_INPUT_KEY + ")");
        ParsedTemplate cancelTemplate = parseTemplate("/" + PROMPT_COMMAND + " cancel " + prompt.token());
        CommonDialogData common = new CommonDialogData(
                net.minecraft.network.chat.Component.literal("Menu Prompt"),
                java.util.Optional.empty(),
                PROMPT_CAN_CLOSE_WITH_ESCAPE,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(net.minecraft.network.chat.Component.literal(request.prompt()), 200)),
                List.of(new Input(PROMPT_INPUT_KEY, new TextInput(
                        220,
                        net.minecraft.network.chat.Component.empty(),
                        false,
                        request.initialValue(),
                        256,
                        java.util.Optional.empty()))));
        ActionButton submit = new ActionButton(
                new CommonButtonData(net.minecraft.network.chat.Component.literal("Submit"), 120),
                java.util.Optional.of(new CommandTemplate(submitTemplate)));
        ActionButton cancel = new ActionButton(
                new CommonButtonData(net.minecraft.network.chat.Component.literal("Cancel"), 120),
                java.util.Optional.of(new CommandTemplate(cancelTemplate)));
        Dialog dialog = new MultiActionDialog(common, List.of(submit), java.util.Optional.of(cancel), 1);
        player.openDialog(Holder.direct(dialog));
    }

    private void completePrompt(PendingTextPrompt prompt, ReactiveMenuInput input) {
        FabricMenuSession session = prompt.session();
        runPromptCompletionPhase(() -> {
            UUID viewerId = session.viewer().getUUID();
            if (!prompts.remove(viewerId, prompt)) {
                return;
            }
            if (sessions.get(viewerId) != session) {
                return;
            }
            long callbackGeneration = session.callbackGeneration();
            List<ReactiveMenuEffect> effects = MenuTrace.time(
                    "runtime.reactiveDispatch",
                    () -> session.invokeUserCallback(() -> session.state().dispatchReactive(input)));
            if (!activeAfterCallback(session, callbackGeneration)) {
                return;
            }
            Runnable applyOrReopen = MenuTrace.propagate(() -> {
                if (sessions.get(viewerId) != session) {
                    return;
                }
                applyPromptCompletionEffects(
                        () -> MenuTrace.time(
                                "runtime.applyEffects", () -> applyEffects(session, session.viewer(), effects)),
                        () -> MenuTrace.time(
                                "session.renderCurrentView", () -> session.renderCurrentView()));
            });
            scheduleNextTick(() -> runPromptCompletionPhase(
                    applyOrReopen, () -> quarantine(session)));
        }, () -> quarantine(session));
    }

    static void runPromptCompletionPhase(Runnable phase, Runnable quarantine) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(quarantine, "quarantine");
        try {
            phase.run();
        } catch (RuntimeException exception) {
            quarantine.run();
        }
    }

    static void runPromptActivationPhase(Runnable activation, Runnable quarantine) {
        runPromptCompletionPhase(activation, quarantine);
    }

    static void applyPromptCompletionEffects(BooleanSupplier applyEffects, Runnable reopen) {
        Objects.requireNonNull(applyEffects, "applyEffects");
        Objects.requireNonNull(reopen, "reopen");
        if (!applyEffects.getAsBoolean()) {
            reopen.run();
        }
    }

    private boolean promptBlocksInventoryOpen(FabricMenuSession session) {
        PendingTextPrompt prompt = prompts.get(session.viewer().getUUID());
        return prompt != null && prompt.session() == session;
    }

    private void removePrompt(FabricMenuSession session) {
        UUID viewerId = session.viewer().getUUID();
        prompts.computeIfPresent(viewerId, (ignored, prompt) ->
                prompt.session() == session ? null : prompt);
    }

    private boolean custodyCallbackStillCurrent(
            FabricMenuSession session,
            ServerPlayer player,
            CustodyCallbackFence fence
    ) {
        return activeAfterCallback(session, fence.callbackGeneration())
                && liveContainer(session, player, fence.container())
                && session.custody() == fence.custody()
                && session.actionVersion() == fence.actionVersion()
                && session.state().revision() == fence.stateRevision();
    }

    private boolean liveContainer(
            FabricMenuSession session,
            ServerPlayer player,
            FabricMenuContainer container
    ) {
        return session.matches(player, container)
                && player.containerMenu == container
                && !container.closed();
    }

    private boolean allowInput(FabricMenuSession session, AcceptedInput input) {
        FabricMenuSession.InputGateResult result = session.acceptInput(input);
        if (result == FabricMenuSession.InputGateResult.ACCEPTED) {
            return true;
        }
        MenuTrace.field("inputGuard", result == FabricMenuSession.InputGateResult.DUPLICATE ? "duplicate" : "tickCap");
        MenuTrace.field("guardInputKind", input.kind());
        MenuTrace.incrementCount("suppressedInputs");
        MenuTrace.incrementCount(result == FabricMenuSession.InputGateResult.DUPLICATE
                ? "suppressedInputDuplicates"
                : "suppressedInputTickCap");
        return false;
    }

    private static ReactiveTextPromptMode resolvePromptMode(ReactiveTextPromptRequest request) {
        return switch (request.preferredMode()) {
            case PROMPT -> ReactiveTextPromptMode.PROMPT;
            case CHAT -> ReactiveTextPromptMode.CHAT;
            default -> ReactiveTextPromptMode.CHAT;
        };
    }

    private static ParsedTemplate parseTemplate(String template) {
        return ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(template))
                .result()
                .orElseThrow(() -> new IllegalArgumentException("Invalid dialog command template: " + template));
    }

    private static MenuStack toMenuStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        MenuStack.Builder builder = MenuStack.builder(FabricMenuIcons.fromItemStack(itemStack))
                .amount(Math.max(1, itemStack.count()));
        net.minecraft.network.chat.Component name = itemStack.getCustomName();
        if (name == null) {
            name = itemStack.getItemName();
        }
        if (name != null) {
            builder.exactName(FabricMenuComponents.toAdventure(name));
        } else {
            builder.name(fallbackName(itemStack.getItem()));
        }
        var lore = itemStack.get(DataComponents.LORE);
        builder.exactLore(lore == null ? List.of() : lore.lines().stream()
                .map(FabricMenuComponents::toAdventure)
                .toList());
        builder.glow(Boolean.TRUE.equals(itemStack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || itemStack.isEnchanted());
        return builder.build();
    }

    private static ItemStack copyItem(ItemStack item) {
        return item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
    }

    private static String fallbackName(net.minecraft.world.item.Item item) {
        String[] parts = BuiltInNameCache.itemName(Objects.requireNonNull(item, "item")).split("_");
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

    private void trace(ServerPlayer player, String cause, Runnable action) {
        MenuTrace.withTrace(traceController, traceSink, "fabric", player.getUUID(), cause, action);
    }

    private void traceInteraction(
            FabricMenuSession session,
            ServerPlayer player,
            String cause,
            Runnable action
    ) {
        try {
            trace(player, cause, action);
        } catch (RuntimeException exception) {
            quarantine(session);
        }
    }

    private static void traceRenderedTitle(FabricMenuSession session) {
        Component title = session.renderedTitle();
        if (title != null) {
            MenuTrace.title(title);
        }
    }

    private static String flatten(Component component) {
        return PLAIN_TEXT.serialize(component);
    }

    private static final class FabricNativeAccess implements FabricMenuCustody.NativeAccess {

        private final FabricMenuSession session;
        private final FabricMenuContainer container;
        private final ServerPlayer player;

        private FabricNativeAccess(FabricMenuSession session, FabricMenuContainer container, ServerPlayer player) {
            this.session = Objects.requireNonNull(session, "session");
            this.container = container;
            this.player = Objects.requireNonNull(player, "player");
        }

        @Override
        public int viewerSize() {
            return STORAGE_SLOT_COUNT;
        }

        @Override
        public ItemStack readViewer(int slot) {
            return player.getInventory().getItem(slot);
        }

        @Override
        public void writeViewer(int slot, ItemStack item) {
            player.getInventory().setItem(slot, copyItem(item));
        }

        @Override
        public ItemStack readCursor() {
            return requireContainer().getCarried();
        }

        @Override
        public void writeCursor(ItemStack item) {
            requireContainer().setCarried(copyItem(item));
        }

        @Override
        public ItemStack readTarget(String key) {
            return requireContainer().topContainer().getItem(targetSlot(key));
        }

        @Override
        public boolean targetReady(String key) {
            ItemStack base = session.custodyBaseItem(key);
            return base != null && ItemStack.matches(readTarget(key), base);
        }

        @Override
        public void writeTarget(String key, ItemStack item) {
            requireContainer().topContainer().setItem(targetSlot(key), copyItem(item));
        }

        @Override
        public boolean drop(ItemStack item) {
            return player.drop(copyItem(item), false) != null;
        }

        private int targetSlot(String key) {
            Integer slot = session.custody().ledger().targetSlots().get(key);
            if (slot == null) {
                throw new IllegalArgumentException("Unknown custody target: " + key);
            }
            return slot;
        }

        private FabricMenuContainer requireContainer() {
            return Objects.requireNonNull(container, "custody container");
        }
    }

    private record CustodyCallbackFence(
            FabricMenuContainer container,
            FabricMenuCustody custody,
            long callbackGeneration,
            long actionVersion,
            long stateRevision
    ) {
    }

    private record ReactiveClickRoute(
            String custodyTarget,
            MenuInteraction interaction,
            boolean acceptsReactiveClick
    ) {
    }

    private sealed interface AcceptedInput permits CompiledClickInput, ReactiveDragInput,
            ReactiveDropCursorInput, ReactiveInventoryClickInput, ReactiveTopClickInput {

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

    private record ReactiveInventoryClickInput(int slot, MenuClick button, boolean shift) implements AcceptedInput {

        @Override
        public String kind() {
            return "reactive-inventory-click";
        }
    }

    private record ReactiveDragInput(MenuClick button, List<Integer> slots) implements AcceptedInput {

        @Override
        public String kind() {
            return "reactive-drag";
        }
    }

    private record ReactiveDropCursorInput(MenuClick button) implements AcceptedInput {

        @Override
        public String kind() {
            return "reactive-drop-cursor";
        }
    }

    private static final class PendingTextPrompt {

        private final FabricMenuSession session;
        private final ReactiveTextPromptRequest request;
        private final ReactiveTextPromptMode mode;
        private final String token;
        private final FabricMenuContainer closingContainer;
        private volatile PendingTextPromptPhase phase;

        private PendingTextPrompt(FabricMenuSession session, ReactiveTextPromptRequest request,
                                  ReactiveTextPromptMode mode, String token, FabricMenuContainer closingContainer,
                                  PendingTextPromptPhase phase) {
            this.session = session;
            this.request = request;
            this.mode = mode;
            this.token = token;
            this.closingContainer = closingContainer;
            this.phase = phase;
        }

        private FabricMenuSession session() {
            return session;
        }

        private ReactiveTextPromptRequest request() {
            return request;
        }

        private ReactiveTextPromptMode mode() {
            return mode;
        }

        private String token() {
            return token;
        }

        private FabricMenuContainer closingContainer() {
            return closingContainer;
        }

        private PendingTextPromptPhase phase() {
            return phase;
        }

        private void phase(PendingTextPromptPhase phase) {
            this.phase = Objects.requireNonNull(phase, "phase");
        }
    }

    private enum PendingTextPromptPhase {
        AWAITING_MENU_CLOSE,
        ACTIVE
    }

    private static final class BuiltInNameCache {

        private BuiltInNameCache() {
        }

        private static String itemName(net.minecraft.world.item.Item item) {
            return Identifier.parse(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString())
                    .getPath()
                    .toLowerCase(java.util.Locale.ROOT);
        }
    }
}
