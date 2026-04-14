package sh.harold.creative.library.menu.fabric;

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
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuDefinition;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.MenuTraceController;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveTextPromptMode;
import sh.harold.creative.library.menu.ReactiveTextPromptRequest;
import sh.harold.creative.library.menu.core.HouseMenuCompiler;
import sh.harold.creative.library.menu.core.MenuTrace;
import sh.harold.creative.library.menu.core.MenuSessionState;
import sh.harold.creative.library.menu.core.MenuTickHandle;
import sh.harold.creative.library.menu.core.MenuTickScheduler;
import sh.harold.creative.library.sound.fabric.FabricServerSoundCuePlatform;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

final class FabricMenuRuntime implements AutoCloseable {

    static final String PROMPT_COMMAND = "creative_library_menu_prompt";

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
        trace(player, "open", () -> {
            FabricMenuSession previous = sessions.remove(player.getUUID());
            if (previous != null) {
                MenuTrace.time("runtime.detachPrevious", previous::detach);
            }
            FabricMenuSession session = MenuTrace.time("runtime.createSession",
                    () -> new FabricMenuSession(this, player, new MenuSessionState(menu)));
            MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
            sessions.put(player.getUUID(), session);
            MenuTrace.time("runtime.show", () -> show(session, true));
        });
    }

    void onTopClick(FabricMenuContainer container, ServerPlayer player, int slot, MenuClick button, boolean shift) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container)) {
            return;
        }
        if (session.state().reactive()) {
            trace(player, "click", () -> {
                MenuTrace.field("path", "reactive");
                MenuTrace.field("slot", slot);
                MenuTrace.field("button", button);
                MenuTrace.title(session.state().currentFrame().title());
                handleReactiveTopClick(session, player, slot, button, shift, container);
            });
            return;
        }
        if (shift) {
            return;
        }
        MenuInteraction interaction = session.state().interaction(slot, button).orElse(null);
        if (interaction == null) {
            return;
        }
        trace(player, "click", () -> {
            MenuTrace.field("path", "compiled");
            MenuTrace.field("slot", slot);
            MenuTrace.field("button", button);
            MenuTrace.title(session.state().currentFrame().title());
            if (!allowInput(session, new CompiledClickInput(slot, button))) {
                return;
            }
            handleDirectInteraction(session, player, button, interaction);
        });
    }

    void onBottomClick(FabricMenuContainer container, ServerPlayer player, int bottomSlot, MenuClick button,
                       boolean shift, ItemStack clickedItem) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container) || !session.state().reactive()) {
            return;
        }
        trace(player, "click", () -> {
            MenuTrace.field("path", "reactive");
            MenuTrace.field("slot", bottomSlot);
            MenuTrace.field("button", button);
            MenuTrace.title(session.state().currentFrame().title());
            if (!allowInput(session, new ReactiveInventoryClickInput(bottomSlot, button, shift))) {
                return;
            }
            handleReactiveInput(session, player, new ReactiveMenuInput.InventoryClick(
                    bottomSlot,
                    button,
                    shift,
                    toMenuStack(clickedItem)),
                    null);
        });
    }

    void onDrag(FabricMenuContainer container, ServerPlayer player, MenuClick button, List<Integer> slots) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container) || !session.state().reactive()) {
            return;
        }
        trace(player, "drag", () -> {
            MenuTrace.field("path", "reactive");
            MenuTrace.field("button", button);
            MenuTrace.setCount("dragSlots", slots.size());
            MenuTrace.title(session.state().currentFrame().title());
            if (!allowInput(session, new ReactiveDragInput(button, slots))) {
                return;
            }
            handleReactiveInput(session, player, new ReactiveMenuInput.Drag(button, slots, toMenuStack(container.getCarried())), null);
        });
    }

    void onDropCursor(FabricMenuContainer container, ServerPlayer player, MenuClick button) {
        FabricMenuSession session = container.session();
        if (sessions.get(player.getUUID()) != session || !session.matches(player, container) || !session.state().reactive()) {
            return;
        }
        trace(player, "drop-cursor", () -> {
            MenuTrace.field("path", "reactive");
            MenuTrace.field("button", button);
            MenuTrace.title(session.state().currentFrame().title());
            if (!allowInput(session, new ReactiveDropCursorInput(button))) {
                return;
            }
            handleReactiveInput(session, player, new ReactiveMenuInput.DropCursor(button, toMenuStack(container.getCarried())), null);
        });
    }

    void onContainerRemoved(FabricMenuContainer container, ServerPlayer player) {
        FabricMenuSession session = container.session();
        session.clearContainer(container);
        trace(player, "close", () -> {
            MenuTrace.title(session.state().currentFrame().title());
            if (advancePromptAfterClose(player.getUUID(), session, container)) {
                return;
            }
            if (container.transitionClosing()) {
                return;
            }
            if (sessions.remove(player.getUUID(), session)) {
                MenuTrace.time("runtime.sessionDetach", session::detach);
            }
        });
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
        FabricMenuSession session = sessions.remove(player.getUUID());
        if (session != null) {
            session.detach();
        }
    }

    void onServerTick() {
        tasks.tick();
    }

    void onTick(FabricMenuSession session) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        trace(session.viewer(), "tick", () -> {
            MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
            MenuTrace.title(session.state().currentFrame().title());
            List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.stateTick", session.state()::tick);
            if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, session.viewer(), effects))) {
                MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
            }
        });
    }

    void refresh(FabricMenuSession session) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        session.state().invalidateView();
        MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
    }

    void replace(FabricMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        MenuTrace.time("runtime.replace", () -> {
            session.state().openChild(menu);
            show(session, true);
        });
    }

    void back(FabricMenuSession session) {
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        MenuTrace.time("runtime.back", () -> {
            if (!session.state().back()) {
                return;
            }
            show(session, true);
        });
    }

    void close(FabricMenuSession session) {
        if (!sessions.remove(session.viewer().getUUID(), session)) {
            return;
        }
        prompts.remove(session.viewer().getUUID());
        MenuTrace.time("runtime.close", () -> {
            FabricMenuContainer container = session.container();
            session.detach();
            if (container != null && session.viewer().containerMenu == container) {
                container.markTransitionClose();
                session.clearContainer(container);
                session.viewer().closeContainer();
            }
        });
    }

    void render(SimpleContainer container, List<MenuSlot> previousSlots, List<MenuSlot> nextSlots,
                net.minecraft.core.HolderLookup.Provider registries) {
        long started = System.nanoTime();
        int changedSlots = 0;
        for (int slot = 0; slot < nextSlots.size(); slot++) {
            MenuSlot nextSlot = nextSlots.get(slot);
            if (previousSlots == null || !nextSlot.equals(previousSlots.get(slot))) {
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
    }

    void syncCursor(FabricMenuContainer container, MenuStack previous, MenuStack next,
                    net.minecraft.core.HolderLookup.Provider registries) {
        if (container == null || Objects.equals(previous, next)) {
            return;
        }
        container.setCarried(renderStack(next, registries));
        container.broadcastChanges();
    }

    MenuTickScheduler tickScheduler() {
        return tasks.intervalScheduler();
    }

    MenuTickHandle scheduleNextTick(Runnable action) {
        return tasks.scheduleNextTick(Objects.requireNonNull(action, "action"));
    }

    @Override
    public void close() {
        sessions.values().forEach(this::close);
        sessions.clear();
        prompts.clear();
    }

    private void handleReactiveTopClick(FabricMenuSession session, ServerPlayer player, int slot, MenuClick button,
                                        boolean shift, FabricMenuContainer container) {
        MenuInteraction interaction = session.state().interaction(slot, button).orElse(null);
        if (interaction != null && !(interaction.action() instanceof MenuSlotAction.Dispatch)) {
            if (!allowInput(session, new ReactiveTopClickInput(slot, button, shift))) {
                return;
            }
            handleDirectInteraction(session, player, button, interaction);
            return;
        }
        if (interaction == null && !session.state().acceptsReactiveClick(slot)) {
            return;
        }
        if (!allowInput(session, new ReactiveTopClickInput(slot, button, shift))) {
            return;
        }
        Object message = interaction != null ? ((MenuSlotAction.Dispatch) interaction.action()).message() : null;
        handleReactiveInput(session, player, new ReactiveMenuInput.Click(
                slot,
                button,
                shift,
                message,
                toMenuStack(container.getCarried()),
                toMenuStack(container.topContainer().getItem(slot))),
                interaction);
    }

    private void handleReactiveInput(FabricMenuSession session, ServerPlayer player, ReactiveMenuInput input, MenuInteraction interaction) {
        if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
            playInteractionSound(player, interaction);
        }
        List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.reactiveDispatch", () -> session.state().dispatchReactive(input));
        if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects))) {
            MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
        }
    }

    private void handleDirectInteraction(FabricMenuSession session, ServerPlayer player, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(player, interaction);
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> {
                session.state().openFrame(openFrame.frameId());
                session.renderCurrentView();
            }
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                long before = session.actionVersion();
                MenuContext context = new MenuContext(click, session.state().frameId(), session.state().values(), session);
                execute.action().execute(context);
                if (sessions.get(session.viewer().getUUID()) == session && session.actionVersion() == before) {
                    session.renderCurrentView();
                }
            }
            case MenuSlotAction.Dispatch ignored -> {
            }
        }
    }

    private boolean applyEffects(FabricMenuSession session, ServerPlayer player, List<ReactiveMenuEffect> effects) {
        for (ReactiveMenuEffect effect : effects) {
            switch (effect) {
                case ReactiveMenuEffect.SetViewerInventorySlot setSlot ->
                        MenuTrace.time("runtime.viewerInventorySetSlot",
                                () -> applyViewerInventorySlot(player, setSlot.slot(), setSlot.stack()));
                case ReactiveMenuEffect.RequestTextPrompt prompt -> {
                    openTextPrompt(session, player, prompt.request());
                    return true;
                }
                case ReactiveMenuEffect.Open open -> {
                    replace(session, open.menu());
                    return true;
                }
                case ReactiveMenuEffect.Close ignored -> {
                    close(session);
                    return true;
                }
            }
        }
        return false;
    }

    FabricMenuContainer openMenu(FabricMenuSession session, ServerPlayer player, int rows, Component title, SimpleContainer topContainer) {
        if (promptBlocksInventoryOpen(session)) {
            return null;
        }
        FabricMenuContainer current = session.container();
        if (current != null && player.containerMenu == current) {
            current.markTransitionClose();
            session.clearContainer(current);
        }
        MenuProvider provider = new SimpleMenuProvider((containerId, inventory, openingPlayer) -> {
            FabricMenuContainer created = new FabricMenuContainer(this, session, containerId, inventory, topContainer, rows);
            session.attachContainer(created);
            return created;
        }, FabricMenuComponents.toNative(title, player.level().registryAccess()));
        player.openMenu(provider);
        FabricMenuContainer opened = session.container();
        if (opened != null) {
            opened.broadcastFullState();
        }
        return opened;
    }

    private void applyViewerInventorySlot(ServerPlayer player, int slot, MenuStack stack) {
        player.getInventory().setItem(slot, renderStack(stack, player.level().registryAccess()));
        player.containerMenu.broadcastChanges();
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
        if (activate) {
            List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.stateOpened", session.state()::opened);
            if (MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, session.viewer(), effects))) {
                return;
            }
        }
        MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
    }

    private void openTextPrompt(FabricMenuSession session, ServerPlayer player, ReactiveTextPromptRequest request) {
        PendingTextPrompt prompt = new PendingTextPrompt(
                session,
                request,
                resolvePromptMode(request),
                Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36),
                session.container(),
                PendingTextPromptPhase.AWAITING_MENU_CLOSE);
        prompts.put(player.getUUID(), prompt);
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
        scheduleNextTick(MenuTrace.propagate(() -> {
            if (prompts.get(viewerId) == prompt && sessions.get(viewerId) == session) {
                activatePrompt(prompt, session.viewer());
            }
        }));
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
                true,
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
        if (!prompts.remove(prompt.session().viewer().getUUID(), prompt)) {
            return;
        }
        FabricMenuSession session = prompt.session();
        if (sessions.get(session.viewer().getUUID()) != session) {
            return;
        }
        List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.reactiveDispatch", () -> session.state().dispatchReactive(input));
        scheduleNextTick(MenuTrace.propagate(() -> {
            if (sessions.get(session.viewer().getUUID()) != session) {
                return;
            }
            if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, session.viewer(), effects))) {
                MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
            }
        }));
    }

    private boolean promptBlocksInventoryOpen(FabricMenuSession session) {
        PendingTextPrompt prompt = prompts.get(session.viewer().getUUID());
        return prompt != null && prompt.session() == session;
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
            if (itemStack.getCustomName() != null) {
                builder.exactName(FabricMenuComponents.toAdventurePlain(name));
            } else {
                builder.name(FabricMenuComponents.toAdventurePlain(name));
            }
        } else {
            builder.name(fallbackName(itemStack.getItem()));
        }
        var lore = itemStack.get(DataComponents.LORE);
        builder.exactLore(lore == null ? List.of() : lore.lines().stream()
                .map(FabricMenuComponents::toAdventurePlain)
                .toList());
        builder.glow(Boolean.TRUE.equals(itemStack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || itemStack.isEnchanted());
        return builder.build();
    }

    private ItemStack renderStack(MenuStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        ItemStack rendered = renderer.render(HouseMenuCompiler.compile(0, stack), registries);
        FabricMenuTooltipMetadata.clear(rendered);
        return rendered;
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

    private static String flatten(Component component) {
        return PLAIN_TEXT.serialize(component);
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
