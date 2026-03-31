package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
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
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuDefinition;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.core.HouseMenuCompiler;
import sh.harold.creative.library.menu.core.MenuSessionState;
import sh.harold.creative.library.menu.core.MenuTickScheduler;
import sh.harold.creative.library.sound.SoundCueService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MinestomMenuRuntime implements AutoCloseable {

    private final Map<UUID, MinestomMenuSession> sessions = new ConcurrentHashMap<>();
    private final MinestomMenuRenderer renderer;
    private final SoundCueService sounds;
    private final MenuTickScheduler tickScheduler;

    MinestomMenuRuntime(MinestomMenuRenderer renderer, SoundCueService sounds) {
        this(renderer, sounds, MenuTickScheduler.unsupported());
    }

    MinestomMenuRuntime(MinestomMenuRenderer renderer, SoundCueService sounds, MenuTickScheduler tickScheduler) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.tickScheduler = Objects.requireNonNull(tickScheduler, "tickScheduler");
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
        MinestomMenuSession previous = sessions.remove(player.getUuid());
        if (previous != null) {
            previous.detach();
        }
        MinestomMenuSession session = new MinestomMenuSession(this, player, new MenuSessionState(menu));
        sessions.put(player.getUuid(), session);
        show(session, true);
    }

    void onInventoryPreClick(InventoryPreClickEvent event) {
        Player player = event.getPlayer();
        MinestomMenuSession session = sessions.get(player.getUuid());
        if (session == null || player.getOpenInventory() != session.inventory()) {
            return;
        }
        Inventory inventory = session.inventory();

        event.setCancelled(true);
        if (session.state().reactive()) {
            handleReactiveClick(session, event, inventory);
            return;
        }

        if (event.getInventory() != inventory) {
            return;
        }

        MenuClick click = toMenuClick(event.getClick());
        if (click == null) {
            return;
        }

        int slot = event.getSlot();
        MenuInteraction interaction = session.state().interaction(slot, click).orElse(null);
        if (interaction == null) {
            return;
        }

        handleDirectInteraction(session, click, interaction);
    }

    void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof Inventory inventory)) {
            return;
        }
        MinestomMenuSession session = session(event.getPlayer(), inventory);
        if (session == null) {
            return;
        }
        if (sessions.remove(event.getPlayer().getUuid(), session)) {
            session.detach();
        }
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
        if (!applyEffects(session, session.state().tick())) {
            session.renderCurrentView();
        }
    }

    void refresh(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        session.renderCurrentView();
    }

    void replace(MinestomMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        session.state().openChild(menu);
        show(session, true);
    }

    void back(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        if (!session.state().back()) {
            return;
        }
        show(session, true);
    }

    void close(MinestomMenuSession session) {
        if (!sessions.remove(session.viewer().getUuid(), session)) {
            return;
        }
        session.detach();
        session.viewer().closeInventory();
    }

    void render(Inventory inventory, List<MenuSlot> previousSlots, List<MenuSlot> nextSlots) {
        for (int slot = 0; slot < nextSlots.size(); slot++) {
            if (previousSlots == null || !nextSlots.get(slot).equals(previousSlots.get(slot))) {
                inventory.setItemStack(slot, renderer.render(nextSlots.get(slot)));
            }
        }
    }

    void syncCursor(Player player, MenuStack previous, MenuStack next) {
        if (Objects.equals(previous, next)) {
            return;
        }
        player.getInventory().setCursorItem(next == null ? ItemStack.AIR : renderer.render(HouseMenuCompiler.compile(0, next)));
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
            handleReactiveInput(session, new ReactiveMenuInput.DropCursor(
                    MenuClick.LEFT,
                    toMenuStack(session.viewer().getInventory().getCursorItem())), null);
            return;
        }
        if (click instanceof Click.RightDropCursor) {
            handleReactiveInput(session, new ReactiveMenuInput.DropCursor(
                    MenuClick.RIGHT,
                    toMenuStack(session.viewer().getInventory().getCursorItem())), null);
            return;
        }

        MenuClick button = toMenuClick(click);
        if (button == null) {
            return;
        }

        AbstractInventory clickedInventory = event.getInventory();
        int slot = event.getSlot();
        if (clickedInventory == inventory && slot >= 0 && slot < inventory.getSize()) {
            MenuInteraction interaction = session.state().interaction(slot, button).orElse(null);
            if (interaction != null && !(interaction.action() instanceof MenuSlotAction.Dispatch)) {
                handleDirectInteraction(session, button, interaction);
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
        handleReactiveInput(session, new ReactiveMenuInput.Drag(
                button,
                filtered,
                toMenuStack(session.viewer().getInventory().getCursorItem())),
                null);
    }

    private void handleReactiveInput(MinestomMenuSession session, ReactiveMenuInput input, MenuInteraction interaction) {
        if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
            playInteractionSound(session.viewer(), interaction);
        }
        if (!applyEffects(session, session.state().dispatchReactive(input))) {
            session.renderCurrentView();
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
        if (activate && applyEffects(session, session.state().opened())) {
            return;
        }
        session.renderCurrentView();
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

    private static MenuClick toMenuClick(Click click) {
        if (click instanceof Click.Left || click instanceof Click.LeftShift) {
            return MenuClick.LEFT;
        }
        if (click instanceof Click.Right || click instanceof Click.RightShift) {
            return MenuClick.RIGHT;
        }
        return null;
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
}
