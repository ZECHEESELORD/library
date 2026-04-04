package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

final class PaperMenuRuntime implements AutoCloseable {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final Map<UUID, PaperMenuSession> sessions = new ConcurrentHashMap<>();
    private final PaperMenuAccess access;
    private final Function<UUID, Player> playerLookup;
    private final PaperMenuSlotRenderer renderer;
    private final SoundCueService sounds;
    private final MenuTickScheduler tickScheduler;
    private final Function<Runnable, MenuTickHandle> nextTickScheduler;
    private final MenuTraceController traceController;
    private final Consumer<String> traceSink;
    private int inventoryInteractionDepth;

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
        this.access = Objects.requireNonNull(access, "access");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.tickScheduler = Objects.requireNonNull(tickScheduler, "tickScheduler");
        this.nextTickScheduler = Objects.requireNonNull(nextTickScheduler, "nextTickScheduler");
        this.traceController = Objects.requireNonNull(traceController, "traceController");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
    }

    void open(Player player, MenuDefinition menu) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        trace(player, "open", () -> {
            UUID viewerId = player.getUniqueId();
            PaperMenuSession previous = sessions.remove(viewerId);
            if (previous != null) {
                MenuTrace.time("runtime.detachPrevious", previous::detach);
            }
            PaperMenuSession session = MenuTrace.time("runtime.createSession",
                    () -> new PaperMenuSession(this, viewerId, new MenuSessionState(menu)));
            MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
            sessions.put(viewerId, session);
            MenuTrace.time("runtime.show", () -> show(session, player, true));
        });
    }

    void onInventoryClick(InventoryClickEvent event) {
        inInventoryInteraction(() -> {
            Inventory topInventory = event.getView().getTopInventory();
            PaperMenuSession session = session(topInventory);
            if (session == null) {
                return;
            }

            HumanEntity whoClicked = event.getWhoClicked();
            if (!(whoClicked instanceof Player player) || sessions.get(player.getUniqueId()) != session || !session.matches(player, topInventory)) {
                return;
            }
            if (session.state().reactive()) {
                trace(player, "click", () -> {
                    MenuTrace.field("path", "reactive");
                    MenuTrace.field("slot", event.getRawSlot());
                    MenuTrace.title(session.state().currentFrame().title());

                    event.setCancelled(true);
                    int rawSlot = event.getRawSlot();

                    if (rawSlot < 0) {
                        MenuTrace.time("runtime.handleReactiveOutsideClick", () -> handleReactiveOutsideClick(session, player, event));
                        return;
                    }
                    if (rawSlot < topInventory.getSize()) {
                        MenuTrace.time("runtime.handleReactiveTopClick", () -> handleReactiveTopClick(session, player, rawSlot, event));
                        return;
                    }
                    MenuTrace.time("runtime.handleReactiveBottomClick", () -> handleReactiveBottomClick(session, player, event));
                });
                return;
            }

            event.setCancelled(true);
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
                MenuTrace.title(session.state().currentFrame().title());
                if (!allowInput(session, new CompiledClickInput(rawSlot, click))) {
                    return;
                }

                MenuTrace.time("runtime.handleDirectInteraction", () -> handleDirectInteraction(session, player, click, interaction));
            });
        });
    }

    void onInventoryDrag(InventoryDragEvent event) {
        inInventoryInteraction(() -> {
            Inventory topInventory = event.getView().getTopInventory();
            PaperMenuSession session = session(topInventory);
            if (session == null || !session.state().reactive()) {
                return;
            }
            HumanEntity whoClicked = event.getWhoClicked();
            if (!(whoClicked instanceof Player player) || sessions.get(player.getUniqueId()) != session || !session.matches(player, topInventory)) {
                return;
            }
            trace(player, "drag", () -> {
                MenuTrace.field("path", "reactive");
                MenuTrace.title(session.state().currentFrame().title());

                List<Integer> slots = new ArrayList<>();
                for (int rawSlot : event.getRawSlots()) {
                    if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
                        slots.add(rawSlot);
                    }
                }
                if (slots.isEmpty()) {
                    return;
                }

                MenuClick button = switch (event.getType()) {
                    case EVEN -> MenuClick.LEFT;
                    case SINGLE -> MenuClick.RIGHT;
                    default -> null;
                };
                if (button == null) {
                    return;
                }
                MenuTrace.field("button", button);
                MenuTrace.setCount("dragSlots", slots.size());

                event.setCancelled(true);
                List<Integer> fingerprintSlots = slots.stream().sorted().toList();
                if (!allowInput(session, new ReactiveDragInput(button, fingerprintSlots))) {
                    return;
                }
                handleReactiveInput(session, player, new ReactiveMenuInput.Drag(button, fingerprintSlots, toMenuStack(event.getOldCursor())), null);
            });
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
            MenuTrace.title(session.state().currentFrame().title());
            if (sessions.remove(player.getUniqueId(), session)) {
                MenuTrace.time("runtime.sessionDetach", session::detach);
            }
        });
    }

    void onPlayerDisconnect(Player player) {
        PaperMenuSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.detach();
        }
    }

    void onTick(PaperMenuSession session) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (player == null) {
            return;
        }
        trace(player, "tick", () -> {
            MenuTrace.field("path", session.state().reactive() ? "reactive" : "compiled");
            MenuTrace.title(session.state().currentFrame().title());
            List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.stateTick", session.state()::tick);
            if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects))) {
                MenuTrace.time("session.refresh", () -> session.refresh(player));
            }
        });
    }

    void refresh(PaperMenuSession session) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            MenuTrace.time("session.refresh", () -> session.refresh(player));
        }
    }

    void replace(PaperMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        MenuTrace.time("runtime.replace", () -> {
            session.state().openChild(menu);
            Player player = playerLookup.apply(session.viewerId());
            if (player != null) {
                show(session, player, true);
            }
        });
    }

    void back(PaperMenuSession session) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        MenuTrace.time("runtime.back", () -> {
            if (!session.state().back()) {
                return;
            }
            Player player = playerLookup.apply(session.viewerId());
            if (player != null) {
                show(session, player, true);
            }
        });
    }

    void close(PaperMenuSession session) {
        if (!sessions.remove(session.viewerId(), session)) {
            return;
        }
        MenuTrace.time("runtime.close", session::detach);
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            if (shouldDeferInventoryTransitions()) {
                Inventory closingInventory = session.inventory();
                scheduleNextTick(MenuTrace.propagate(() -> {
                    if (access.topInventory(player) == closingInventory) {
                        MenuTrace.time("runtime.inventoryClose", () -> access.closeInventory(player));
                    }
                }));
            } else {
                MenuTrace.time("runtime.inventoryClose", () -> access.closeInventory(player));
            }
        }
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

    void syncCursor(Player player, MenuStack previous, MenuStack next) {
        if (Objects.equals(previous, next)) {
            return;
        }
        MenuTrace.time("runtime.cursorSync",
                () -> player.setItemOnCursor(next == null ? null : renderer.render(HouseMenuCompiler.compile(0, next))));
    }

    PaperMenuAccess access() {
        return access;
    }

    MenuTickScheduler tickScheduler() {
        return tickScheduler;
    }

    MenuTickHandle scheduleNextTick(Runnable action) {
        return nextTickScheduler.apply(Objects.requireNonNull(action, "action"));
    }

    @Override
    public void close() {
        sessions.values().forEach(this::close);
        sessions.clear();
    }

    private void handleReactiveOutsideClick(PaperMenuSession session, Player player, InventoryClickEvent event) {
        ReactiveClickBinding click = toReactiveClick(event.getClick());
        if (click == null) {
            return;
        }
        MenuTrace.field("button", click.button());
        if (!allowInput(session, new ReactiveDropCursorInput(click.button()))) {
            return;
        }
        handleReactiveInput(session, player, new ReactiveMenuInput.DropCursor(click.button(), toMenuStack(event.getCursor())), null);
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
        handleReactiveInput(session, player, new ReactiveMenuInput.Click(
                rawSlot,
                click.button(),
                click.shift(),
                message,
                toMenuStack(event.getCursor()),
                toMenuStack(event.getCurrentItem())),
                interaction);
    }

    private void handleReactiveBottomClick(PaperMenuSession session, Player player, InventoryClickEvent event) {
        ReactiveClickBinding click = toReactiveClick(event.getClick());
        if (click == null) {
            return;
        }
        MenuTrace.field("button", click.button());
        int slot = event.getSlot();
        if (slot < 0) {
            return;
        }
        if (!allowInput(session, new ReactiveInventoryClickInput(slot, click.button(), click.shift()))) {
            return;
        }
        handleReactiveInput(session, player, new ReactiveMenuInput.InventoryClick(
                slot,
                click.button(),
                click.shift(),
                toMenuStack(event.getCurrentItem())),
                null);
    }

    private void handleReactiveInput(PaperMenuSession session, Player player, ReactiveMenuInput input, MenuInteraction interaction) {
        if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
            playInteractionSound(player, interaction);
        }
        List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.reactiveDispatch", () -> session.state().dispatchReactive(input));
        if (!MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects))) {
            MenuTrace.time("session.refresh", () -> session.refresh(player));
        }
    }

    private void handleDirectInteraction(PaperMenuSession session, Player player, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(player, interaction);
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> {
                session.state().openFrame(openFrame.frameId());
                MenuTrace.time("session.refresh", () -> session.refresh(player));
            }
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                long before = session.actionVersion();
                MenuContext context = new MenuContext(click, session.state().frameId(), session.state().values(), session);
                execute.action().execute(context);
                if (sessions.get(session.viewerId()) == session && session.actionVersion() == before) {
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
                case ReactiveMenuEffect.SetViewerInventorySlot setSlot ->
                        MenuTrace.time("runtime.viewerInventorySetSlot",
                                () -> applyViewerInventorySlot(player, setSlot.slot(), setSlot.stack()));
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

    private void applyViewerInventorySlot(Player player, int slot, MenuStack stack) {
        player.getInventory().setItem(slot, renderStack(stack));
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

    private void show(PaperMenuSession session, Player player, boolean activate) {
        if (activate) {
            List<ReactiveMenuEffect> effects = MenuTrace.time("runtime.stateOpened", session.state()::opened);
            if (MenuTrace.time("runtime.applyEffects", () -> applyEffects(session, player, effects))) {
                return;
            }
        }
        MenuTrace.time("session.refresh", () -> session.refresh(player));
    }

    boolean shouldDeferInventoryTransitions() {
        return inventoryInteractionDepth > 0;
    }

    void openInventory(PaperMenuSession session, Player player, Inventory inventory) {
        if (!shouldDeferInventoryTransitions()) {
            MenuTrace.time("runtime.inventoryOpen", () -> access.openInventory(player, inventory));
            return;
        }
        scheduleNextTick(MenuTrace.propagate(() -> {
            if (sessions.get(session.viewerId()) == session && session.inventory() == inventory) {
                MenuTrace.time("runtime.inventoryOpen", () -> access.openInventory(player, inventory));
            }
        }));
    }

    private PaperMenuSession session(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        if (!(inventory.getHolder(false) instanceof PaperMenuSession session)) {
            return null;
        }
        return session.inventory() == inventory ? session : null;
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
            case LEFT -> new ReactiveClickBinding(MenuClick.LEFT, false);
            case SHIFT_LEFT -> new ReactiveClickBinding(MenuClick.LEFT, true);
            case RIGHT -> new ReactiveClickBinding(MenuClick.RIGHT, false);
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

    private ItemStack renderStack(MenuStack stack) {
        return stack == null ? null : renderer.render(HouseMenuCompiler.compile(0, stack));
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

    private void inInventoryInteraction(Runnable action) {
        inventoryInteractionDepth++;
        try {
            action.run();
        } finally {
            inventoryInteractionDepth--;
        }
    }

    private record ReactiveClickBinding(MenuClick button, boolean shift) {
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
}
