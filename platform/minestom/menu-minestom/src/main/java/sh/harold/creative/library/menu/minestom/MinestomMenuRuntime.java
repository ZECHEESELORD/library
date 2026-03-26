package sh.harold.creative.library.menu.minestom;

import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.click.Click;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.core.MenuSessionState;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MinestomMenuRuntime implements AutoCloseable {

    private final Map<UUID, MinestomMenuSession> sessions = new ConcurrentHashMap<>();
    private final MinestomMenuRenderer renderer;

    MinestomMenuRuntime(MinestomMenuRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    EventNode<Event> createEventNode(String name) {
        EventNode<Event> node = EventNode.all(name);
        node.addListener(InventoryPreClickEvent.class, this::onInventoryPreClick);
        node.addListener(InventoryCloseEvent.class, this::onInventoryClose);
        node.addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        return node;
    }

    void open(Player player, Menu menu) {
        UUID viewerId = player.getUuid();
        MinestomMenuSession session = new MinestomMenuSession(this, player, new MenuSessionState(menu));
        sessions.put(viewerId, session);
        session.open();
    }

    void onInventoryPreClick(InventoryPreClickEvent event) {
        if (!(event.getInventory() instanceof Inventory inventory)) {
            return;
        }
        MinestomMenuSession session = session(event.getPlayer(), inventory);
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        MenuClick click = toMenuClick(event.getClick());
        if (click == null) {
            return;
        }

        int slot = event.getSlot();
        MenuInteraction interaction = session.state().interaction(slot, click).orElse(null);
        if (interaction == null) {
            return;
        }

        handleInteraction(session, click, interaction);
    }

    void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof Inventory inventory)) {
            return;
        }
        MinestomMenuSession session = session(event.getPlayer(), inventory);
        if (session == null) {
            return;
        }
        sessions.remove(event.getPlayer().getUuid(), session);
    }

    void onPlayerDisconnect(PlayerDisconnectEvent event) {
        sessions.remove(event.getPlayer().getUuid());
    }

    void refresh(MinestomMenuSession session) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        session.renderCurrentFrame();
    }

    void replace(MinestomMenuSession session, Menu menu) {
        if (sessions.get(session.viewer().getUuid()) != session) {
            return;
        }
        session.state().open(menu);
        session.renderCurrentFrame();
    }

    void close(MinestomMenuSession session) {
        if (!sessions.remove(session.viewer().getUuid(), session)) {
            return;
        }
        session.viewer().closeInventory();
    }

    void render(MinestomMenuSession session, Inventory inventory) {
        for (int slot = 0; slot < session.state().currentFrame().slots().size(); slot++) {
            inventory.setItemStack(slot, renderer.render(session.state().currentFrame().slots().get(slot)));
        }
    }

    @Override
    public void close() {
        sessions.values().forEach(this::close);
        sessions.clear();
    }

    private void handleInteraction(MinestomMenuSession session, MenuClick click, MenuInteraction interaction) {
        switch (interaction.action()) {
            case MenuSlotAction.OpenFrame openFrame -> {
                session.state().openFrame(openFrame.frameId());
                session.renderCurrentFrame();
            }
            case MenuSlotAction.Close ignored -> close(session);
            case MenuSlotAction.Execute execute -> {
                MenuContext context = new MenuContext(click, session.state().frameId(), session.state().values(), session);
                execute.action().execute(context);
                if (sessions.get(session.viewer().getUuid()) == session) {
                    session.renderCurrentFrame();
                }
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

    private static MenuClick toMenuClick(Click click) {
        if (click instanceof Click.Left || click instanceof Click.LeftShift) {
            return MenuClick.LEFT;
        }
        if (click instanceof Click.Right || click instanceof Click.RightShift) {
            return MenuClick.RIGHT;
        }
        return null;
    }
}
