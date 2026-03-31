package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.HumanEntity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.core.HouseMenuCompiler;
import sh.harold.creative.library.menu.core.MenuSessionState;
import sh.harold.creative.library.menu.core.MenuTickScheduler;
import sh.harold.creative.library.sound.SoundCueService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class PaperMenuRuntime implements AutoCloseable {

    private final Map<UUID, PaperMenuSession> sessions = new ConcurrentHashMap<>();
    private final PaperMenuAccess access;
    private final Function<UUID, Player> playerLookup;
    private final PaperMenuSlotRenderer renderer;
    private final SoundCueService sounds;
    private final MenuTickScheduler tickScheduler;

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer, SoundCueService sounds) {
        this(access, playerLookup, renderer, sounds, MenuTickScheduler.unsupported());
    }

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer,
                     SoundCueService sounds, MenuTickScheduler tickScheduler) {
        this.access = Objects.requireNonNull(access, "access");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.tickScheduler = Objects.requireNonNull(tickScheduler, "tickScheduler");
    }

    void open(Player player, MenuDefinition menu) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        UUID viewerId = player.getUniqueId();
        PaperMenuSession previous = sessions.remove(viewerId);
        if (previous != null) {
            previous.detach();
        }
        PaperMenuSession session = new PaperMenuSession(this, viewerId, new MenuSessionState(menu));
        sessions.put(viewerId, session);
        show(session, player, true);
    }

    void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        PaperMenuSession session = session(topInventory);
        if (session == null) {
            return;
        }

        HumanEntity whoClicked = event.getWhoClicked();
        if (!(whoClicked instanceof Player player) || sessions.get(player.getUniqueId()) != session || !session.matches(player, topInventory)) {
            return;
        }

        event.setCancelled(true);
        int rawSlot = event.getRawSlot();

        if (session.state().reactive()) {
            if (rawSlot < 0) {
                handleReactiveOutsideClick(session, player, event);
                return;
            }
            if (rawSlot < topInventory.getSize()) {
                handleReactiveTopClick(session, player, rawSlot, event);
                return;
            }
            handleReactiveBottomClick(session, player, event);
            return;
        }

        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
            return;
        }

        MenuClick click = toMenuClick(event);
        if (click == null) {
            return;
        }

        MenuInteraction interaction = session.state().interaction(rawSlot, click).orElse(null);
        if (interaction == null) {
            return;
        }

        handleDirectInteraction(session, player, click, interaction);
    }

    void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        PaperMenuSession session = session(topInventory);
        if (session == null || !session.state().reactive()) {
            return;
        }
        HumanEntity whoClicked = event.getWhoClicked();
        if (!(whoClicked instanceof Player player) || sessions.get(player.getUniqueId()) != session || !session.matches(player, topInventory)) {
            return;
        }

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

        event.setCancelled(true);
        handleReactiveInput(session, player, new ReactiveMenuInput.Drag(button, slots, toMenuStack(event.getOldCursor())), null);
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

        if (sessions.remove(player.getUniqueId(), session)) {
            session.detach();
        }
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
        if (!applyEffects(session, player, session.state().tick())) {
            session.refresh(player);
        }
    }

    void refresh(PaperMenuSession session) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            session.refresh(player);
        }
    }

    void replace(PaperMenuSession session, MenuDefinition menu) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        session.state().openChild(menu);
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            show(session, player, true);
        }
    }

    void back(PaperMenuSession session) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        if (!session.state().back()) {
            return;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            show(session, player, true);
        }
    }

    void close(PaperMenuSession session) {
        if (!sessions.remove(session.viewerId(), session)) {
            return;
        }
        session.detach();
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            access.closeInventory(player);
        }
    }

    void render(Inventory inventory, List<MenuSlot> previousSlots, List<MenuSlot> nextSlots) {
        for (int slot = 0; slot < nextSlots.size(); slot++) {
            if (previousSlots == null || !nextSlots.get(slot).equals(previousSlots.get(slot))) {
                inventory.setItem(slot, renderer.render(nextSlots.get(slot)));
            }
        }
    }

    void syncCursor(Player player, MenuStack previous, MenuStack next) {
        if (Objects.equals(previous, next)) {
            return;
        }
        player.setItemOnCursor(next == null ? null : renderer.render(HouseMenuCompiler.compile(0, next)));
    }

    PaperMenuAccess access() {
        return access;
    }

    MenuTickScheduler tickScheduler() {
        return tickScheduler;
    }

    @Override
    public void close() {
        sessions.values().forEach(this::close);
        sessions.clear();
    }

    private void handleReactiveOutsideClick(PaperMenuSession session, Player player, InventoryClickEvent event) {
        MenuClick click = toMenuClick(event);
        if (click == null) {
            return;
        }
        handleReactiveInput(session, player, new ReactiveMenuInput.DropCursor(click, toMenuStack(event.getCursor())), null);
    }

    private void handleReactiveTopClick(PaperMenuSession session, Player player, int rawSlot, InventoryClickEvent event) {
        MenuClick click = toMenuClick(event);
        if (click == null) {
            return;
        }
        MenuInteraction interaction = session.state().interaction(rawSlot, click).orElse(null);
        if (interaction != null && !(interaction.action() instanceof MenuSlotAction.Dispatch)) {
            handleDirectInteraction(session, player, click, interaction);
            return;
        }
        Object message = interaction != null ? ((MenuSlotAction.Dispatch) interaction.action()).message() : null;
        handleReactiveInput(session, player, new ReactiveMenuInput.Click(
                rawSlot,
                click,
                event.isShiftClick(),
                message,
                toMenuStack(event.getCursor()),
                toMenuStack(event.getCurrentItem())),
                interaction);
    }

    private void handleReactiveBottomClick(PaperMenuSession session, Player player, InventoryClickEvent event) {
        MenuClick click = toMenuClick(event);
        if (click == null) {
            return;
        }
        int slot = event.getSlot();
        if (slot < 0) {
            return;
        }
        handleReactiveInput(session, player, new ReactiveMenuInput.InventoryClick(
                slot,
                click,
                event.isShiftClick(),
                toMenuStack(event.getCurrentItem())),
                null);
    }

    private void handleReactiveInput(PaperMenuSession session, Player player, ReactiveMenuInput input, MenuInteraction interaction) {
        if (interaction != null && interaction.action() instanceof MenuSlotAction.Dispatch) {
            playInteractionSound(player, interaction);
        }
        if (!applyEffects(session, player, session.state().dispatchReactive(input))) {
            session.refresh(player);
        }
    }

    private void handleDirectInteraction(PaperMenuSession session, Player player, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(player, interaction);
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> {
                session.state().openFrame(openFrame.frameId());
                session.refresh(player);
            }
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                long before = session.actionVersion();
                MenuContext context = new MenuContext(click, session.state().frameId(), session.state().values(), session);
                execute.action().execute(context);
                if (sessions.get(session.viewerId()) == session && session.actionVersion() == before) {
                    session.refresh(player);
                }
            }
            case MenuSlotAction.Dispatch ignored -> {
            }
        }
    }

    private boolean applyEffects(PaperMenuSession session, Player player, List<ReactiveMenuEffect> effects) {
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

    private void show(PaperMenuSession session, Player player, boolean activate) {
        if (activate && applyEffects(session, player, session.state().opened())) {
            return;
        }
        session.refresh(player);
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

    private static MenuClick toMenuClick(InventoryClickEvent event) {
        if (event.isLeftClick()) {
            return MenuClick.LEFT;
        }
        if (event.isRightClick()) {
            return MenuClick.RIGHT;
        }
        return null;
    }

    private static MenuStack toMenuStack(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        Material type = itemStack.getType();
        if (type == null || type == Material.AIR) {
            return null;
        }
        MenuStack.Builder builder = MenuStack.builder(MenuIcon.vanilla(type.getKey().asString()))
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
}
