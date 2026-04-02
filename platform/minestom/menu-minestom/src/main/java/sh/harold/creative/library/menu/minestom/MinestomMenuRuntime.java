package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;
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
import sh.harold.creative.library.menu.core.HouseMenuCompiler;
import sh.harold.creative.library.menu.core.MenuTrace;
import sh.harold.creative.library.menu.core.MenuSessionState;
import sh.harold.creative.library.menu.core.MenuTickHandle;
import sh.harold.creative.library.menu.core.MenuTickScheduler;
import sh.harold.creative.library.sound.SoundCueService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

final class MinestomMenuRuntime implements AutoCloseable {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final Map<UUID, MinestomMenuSession> sessions = new ConcurrentHashMap<>();
    private final MinestomMenuRenderer renderer;
    private final SoundCueService sounds;
    private final MenuTickScheduler tickScheduler;
    private final Function<Runnable, MenuTickHandle> nextTickScheduler;
    private final MenuTraceController traceController;
    private final Consumer<String> traceSink;

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
        node.addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        return node;
    }

    void open(Player player, MenuDefinition menu) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        trace(player, "open", () -> {
            MinestomMenuSession previous = sessions.remove(player.getUuid());
            if (previous != null) {
                MenuTrace.time("runtime.detachPrevious", previous::detach);
            }
            MinestomMenuSession session = MenuTrace.time("runtime.createSession",
                    () -> new MinestomMenuSession(this, player, new MenuSessionState(menu)));
            MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
            sessions.put(player.getUuid(), session);
            MenuTrace.time("runtime.show", () -> show(session, true));
        });
    }

    void onInventoryPreClick(InventoryPreClickEvent event) {
        Player player = event.getPlayer();
        MinestomMenuSession session = sessions.get(player.getUuid());
        if (session == null || player.getOpenInventory() != session.inventory()) {
            return;
        }
        Inventory inventory = session.inventory();
        if (session.state().reactive()) {
            trace(player, cause(event.getClick()), () -> {
                MenuTrace.field("path", "reactive");
                MenuTrace.field("slot", event.getSlot());
                MenuTrace.title(session.state().currentFrame().title());

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
            MenuTrace.title(session.state().currentFrame().title());

            MenuTrace.time("runtime.handleDirectInteraction", () -> handleDirectInteraction(session, click, interaction));
        });
    }

    void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof Inventory inventory)) {
            return;
        }
        MinestomMenuSession session = session(event.getPlayer(), inventory);
        if (session == null) {
            return;
        }
        trace(event.getPlayer(), "close", () -> {
            MenuTrace.title(session.state().currentFrame().title());
            if (sessions.remove(event.getPlayer().getUuid(), session)) {
                MenuTrace.time("runtime.sessionDetach", session::detach);
            }
        });
    }

    void onPlayerDisconnect(PlayerDisconnectEvent event) {
        MinestomMenuSession session = sessions.remove(event.getPlayer().getUuid());
        if (session != null) {
            session.detach();
        }
    }

    void onTick(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        trace(session.viewer(), "tick", () -> {
            MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
            MenuTrace.title(session.state().currentFrame().title());
            List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.stateTick", session.state()::tick);
            if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, effects))) {
                MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
            }
        });
    }

    void refresh(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
    }

    void replace(MinestomMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        MenuTrace.time("runtime.replace", () -> {
            session.state().openChild(menu);
            show(session, true);
        });
    }

    void back(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        MenuTrace.time("runtime.back", () -> {
            if (!session.state().back()) {
                return;
            }
            show(session, true);
        });
    }

    void close(MinestomMenuSession session) {
        if (!sessions.remove(session.viewer().getUuid(), session)) {
            return;
        }
        MenuTrace.time("runtime.close", () -> {
            session.detach();
            MenuTrace.time("runtime.inventoryClose", () -> {
                session.viewer().closeInventory();
            });
        });
    }

    void render(Inventory inventory, List<MenuSlot> previousSlots, List<MenuSlot> nextSlots) {
        long started = System.nanoTime();
        int changedSlots = 0;
        for (int slot = 0; slot < nextSlots.size(); slot++) {
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

    void syncCursor(Player player, MenuStack previous, MenuStack next) {
        if (Objects.equals(previous, next)) {
            return;
        }
        MenuTrace.time("runtime.cursorSync", () ->
                player.getInventory().setCursorItem(next == null ? ItemStack.AIR : renderer.render(HouseMenuCompiler.compile(0, next))));
    }

    MenuTickScheduler tickScheduler() {
        return tickScheduler;
    }

    @Override
    public void close() {
        sessions.values().forEach(this::close);
        sessions.clear();
    }

    private void handleReactiveClick(MinestomMenuSession session, InventoryPreClickEvent event, Inventory inventory) {
        Click click = event.getClick();
        if (click instanceof Click.LeftDrag leftDrag) {
            handleReactiveDrag(session, MenuClick.LEFT, leftDrag.slots(), inventory);
            return;
        }
        if (click instanceof Click.RightDrag rightDrag) {
            handleReactiveDrag(session, MenuClick.RIGHT, rightDrag.slots(), inventory);
            return;
        }
        if (click instanceof Click.LeftDropCursor) {
            handleReactiveDropCursor(session, MenuClick.LEFT);
            return;
        }
        if (click instanceof Click.RightDropCursor) {
            handleReactiveDropCursor(session, MenuClick.RIGHT);
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
                    message,
                    toMenuStack(session.viewer().getInventory().getCursorItem()),
                    toMenuStack(event.getClickedItem())),
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
        if (!session.tryAcquireInputGuard()) {
            trace(session.viewer(), cause(click), () -> recordSuppressedInput("reactive-bottom", bottomSlot, button, "tick-cap"));
            return;
        }
        handleReactiveInput(session, new ReactiveMenuInput.InventoryClick(
                bottomSlot,
                button,
                isShiftClick(click),
                toMenuStack(event.getClickedItem())),
                null);
    }

    private void handleReactiveDrag(MinestomMenuSession session, MenuClick button, List<Integer> slots, Inventory inventory) {
        List<Integer> filtered = slots.stream()
                .filter(slot -> slot >= 0 && slot < inventory.getSize())
                .toList();
        if (filtered.isEmpty()) {
            return;
        }
        if (!session.tryAcquireInputGuard()) {
            trace(session.viewer(), "drag", () -> recordSuppressedInput("reactive-drag", filtered.get(0), button, "tick-cap"));
            return;
        }
        MenuTrace.field("button", button);
        MenuTrace.setCount("dragSlots", filtered.size());
        handleReactiveInput(session, new ReactiveMenuInput.Drag(
                button,
                filtered,
                toMenuStack(session.viewer().getInventory().getCursorItem())),
                null);
    }

    private void handleReactiveDropCursor(MinestomMenuSession session, MenuClick button) {
        if (!session.tryAcquireInputGuard()) {
            trace(session.viewer(), "drop-cursor", () -> recordSuppressedInput("reactive-drop-cursor", -1, button, "tick-cap"));
            return;
        }
        MenuTrace.field("button", button);
        handleReactiveInput(session, new ReactiveMenuInput.DropCursor(
                button,
                toMenuStack(session.viewer().getInventory().getCursorItem())),
                null);
    }

    private void handleReactiveInput(MinestomMenuSession session, ReactiveMenuInput input, MenuInteraction interaction) {
        if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
            playInteractionSound(session.viewer(), interaction);
        }
        List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.reactiveDispatch", () -> session.state().dispatchReactive(input));
        if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, effects))) {
            MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
        }
    }

    private void handleDirectInteraction(MinestomMenuSession session, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(session.viewer(), interaction);
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
                if (sessions.get(session.viewer().getUuid()) == session && session.actionVersion() == before) {
                    session.renderCurrentView();
                }
            }
            case MenuSlotAction.Dispatch ignored -> {
            }
        }
    }

    private boolean applyEffects(MinestomMenuSession session, List<ReactiveMenuEffect> effects) {
        for (ReactiveMenuEffect effect : effects) {
            switch (effect) {
                case ReactiveMenuEffect.SetViewerInventorySlot setSlot ->
                        MenuTrace.time("runtime.viewerInventorySetSlot",
                                () -> applyViewerInventorySlot(session.viewer(), setSlot.slot(), setSlot.stack()));
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

    void rearmInputGuard(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        MenuTrace.time("runtime.inputGuardRearm", session::rearmInputGuard);
    }

    MenuTickHandle scheduleNextTick(Runnable action) {
        return MenuTrace.time("runtime.inputGuardScheduleTask", () -> nextTickScheduler.apply(Objects.requireNonNull(action, "action")));
    }

    private void applyViewerInventorySlot(Player player, int slot, MenuStack stack) {
        player.getInventory().setItemStack(slot, renderStack(stack));
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

    private void show(MinestomMenuSession session, boolean activate) {
        if (activate) {
            List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.stateOpened", session.state()::opened);
            if (MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, effects))) {
                return;
            }
        }
        MenuTrace.time("session.renderCurrentView", session::renderCurrentView);
    }

    private MinestomMenuSession session(Player player, Inventory inventory) {
        MinestomMenuSession session = sessions.get(player.getUuid());
        if (session == null || session.inventory() != inventory) {
            return null;
        }
        return session;
    }

    private static boolean isShiftClick(Click click) {
        return click instanceof Click.LeftShift || click instanceof Click.RightShift;
    }

    private static MenuClick toCompiledMenuClick(Click click) {
        if (click instanceof Click.Left) {
            return MenuClick.LEFT;
        }
        if (click instanceof Click.Right) {
            return MenuClick.RIGHT;
        }
        return null;
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

    private static MenuStack toMenuStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.isAir()) {
            return null;
        }
        Material material = itemStack.material();
        if (material == Material.AIR) {
            return null;
        }
        MenuStack.Builder builder = MenuStack.builder(MenuIcon.vanilla(material.key().asString()))
                .amount(Math.max(1, itemStack.amount()));
        Component name = itemStack.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            builder.name(name);
        } else {
            builder.name(fallbackName(material));
        }
        return builder.build();
    }

    private ItemStack renderStack(MenuStack stack) {
        return stack == null ? ItemStack.AIR : renderer.render(HouseMenuCompiler.compile(0, stack));
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
}
