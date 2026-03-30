package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.key.Key;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.core.MenuSessionState;
import sh.harold.creative.library.sound.SoundCueService;

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

    PaperMenuRuntime(PaperMenuAccess access, Function<UUID, Player> playerLookup, PaperMenuSlotRenderer renderer, SoundCueService sounds) {
        this.access = Objects.requireNonNull(access, "access");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    void open(Player player, Menu menu) {
        UUID viewerId = player.getUniqueId();
        PaperMenuSession session = new PaperMenuSession(this, viewerId, new MenuSessionState(menu));
        sessions.put(viewerId, session);
        session.open(player);
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

        handleInteraction(session, player, click, interaction);
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

        sessions.remove(player.getUniqueId(), session);
    }

    void onPlayerDisconnect(Player player) {
        sessions.remove(player.getUniqueId());
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

    void replace(PaperMenuSession session, Menu menu) {
        if (sessions.get(session.viewerId()) != session) {
            return;
        }
        session.state().openChild(menu);
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            session.refresh(player);
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
            session.refresh(player);
        }
    }

    void close(PaperMenuSession session) {
        if (!sessions.remove(session.viewerId(), session)) {
            return;
        }
        Player player = playerLookup.apply(session.viewerId());
        if (player != null) {
            access.closeInventory(player);
        }
    }

    void render(PaperMenuSession session, Inventory inventory) {
        for (int slot = 0; slot < session.state().currentFrame().slots().size(); slot++) {
            inventory.setItem(slot, renderer.render(session.state().currentFrame().slots().get(slot)));
        }
    }

    PaperMenuAccess access() {
        return access;
    }

    @Override
    public void close() {
        sessions.values().forEach(this::close);
        sessions.clear();
    }

    private void handleInteraction(PaperMenuSession session, Player player, MenuClick click, MenuInteraction interaction) {
        playInteractionSound(player, interaction);
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> {
                session.state().openFrame(openFrame.frameId());
                session.refresh(player);
            }
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                MenuContext context = new MenuContext(click, session.state().frameId(), session.state().values(), session);
                execute.action().execute(context);
                if (sessions.get(session.viewerId()) == session) {
                    session.refresh(player);
                }
            }
        }
    }

    private void playInteractionSound(Player player, MenuInteraction interaction) {
        Key soundCueKey = interaction.soundCueKey();
        if (soundCueKey != null) {
            sounds.play(player, soundCueKey);
        }
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
}
