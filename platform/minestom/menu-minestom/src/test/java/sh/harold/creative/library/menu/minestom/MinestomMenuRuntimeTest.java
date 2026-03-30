package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.click.Click;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.core.StandardMenuService;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinestomMenuRuntimeTest {

    private static boolean serverInitialized;

    @BeforeAll
    static void initServer() {
        if (!serverInitialized) {
            MinecraftServer.init();
            serverInitialized = true;
        }
    }

    @Test
    void openClickNavigateAndCloseUsesOwnedInventoryIdentity() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());
        Menu menu = pagedMenu();

        runtime.open(player, menu);

        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Back", slotTitle(inventory, 45));
        assertEquals("Close", slotTitle(inventory, 49));
        assertEquals("Next Page", slotTitle(inventory, 53));

        InventoryPreClickEvent nextPage = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(nextPage);

        assertTrue(nextPage.isCancelled());
        assertEquals("Previous Page", slotTitle(inventory, 45));
        assertEquals("Back", slotTitle(inventory, 46));
        assertEquals("Next Page", slotTitle(inventory, 53));

        InventoryPreClickEvent close = new InventoryPreClickEvent(inventory, player, new Click.Left(49));
        runtime.onInventoryPreClick(close);
        assertTrue(close.isCancelled());
        assertEquals(1, player.closeCount());
    }

    @Test
    void actionCanReplaceCurrentMenuAndRefreshRenderedContents() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, toggleMenu(enabled));
        Inventory inventory = player.lastOpenedInventory();
        assertEquals("Disabled", slotTitle(inventory, 9));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(9)));

        assertEquals("Enabled", slotTitle(inventory, 9));
        assertEquals(1, player.openCount());
    }

    @Test
    void closeAndSpoofedInventoriesDoNotRouteByTitle() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());
        runtime.open(player, pagedMenu());
        Inventory inventory = player.lastOpenedInventory();

        Inventory spoofedInventory = new Inventory(inventory.getInventoryType(), Component.text("Profiles"));
        InventoryPreClickEvent spoofedClick = new InventoryPreClickEvent(spoofedInventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(spoofedClick);
        assertFalse(spoofedClick.isCancelled());

        runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

        InventoryPreClickEvent staleClick = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(staleClick);
        assertFalse(staleClick.isCancelled());
    }

    @Test
    void disconnectCleansUpViewerSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());
        runtime.open(player, pagedMenu());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onPlayerDisconnect(new PlayerDisconnectEvent(player));

        InventoryPreClickEvent click = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(click);
        assertFalse(click.isCancelled());
    }

    @Test
    void backAndTabControlsRouteThroughOwnedSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());
        AtomicBoolean backed = new AtomicBoolean(false);

        runtime.open(player, galleryMenu(backed));
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Back", slotTitle(inventory, 45));
        assertEquals("Profiles", slotTitle(inventory, 3));
        assertEquals("Progress", slotTitle(inventory, 4));
        assertEquals("Your SkyBlock Profile", slotTitle(inventory, 19));

        InventoryPreClickEvent switchTab = new InventoryPreClickEvent(inventory, player, new Click.Left(4));
        runtime.onInventoryPreClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Profiles", slotTitle(inventory, 3));
        assertEquals("Progress", slotTitle(inventory, 4));
        assertEquals("Farming XLIX", slotTitle(inventory, 19));

        InventoryPreClickEvent back = new InventoryPreClickEvent(inventory, player, new Click.Left(45));
        runtime.onInventoryPreClick(back);

        assertTrue(back.isCancelled());
        assertTrue(backed.get());
    }

    @Test
    void navArrowsScrollStripWithoutChangingActiveContent() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());

        runtime.open(player, overflowGalleryMenu());
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Previous Tabs", slotTitle(inventory, 0));
        assertEquals("Next Tabs", slotTitle(inventory, 8));
        assertEquals("Tab 0", slotTitle(inventory, 1));
        assertEquals("Tab 6", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent scrollRight = new InventoryPreClickEvent(inventory, player, new Click.Left(8));
        runtime.onInventoryPreClick(scrollRight);

        assertTrue(scrollRight.isCancelled());
        assertEquals("Tab 1", slotTitle(inventory, 1));
        assertEquals("Tab 7", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent jumpEnd = new InventoryPreClickEvent(inventory, player, new Click.Right(8));
        runtime.onInventoryPreClick(jumpEnd);

        assertTrue(jumpEnd.isCancelled());
        assertEquals("Tab 3", slotTitle(inventory, 1));
        assertEquals("Tab 9", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent switchTab = new InventoryPreClickEvent(inventory, player, new Click.Left(7));
        runtime.onInventoryPreClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Tab 9 Item 0", slotTitle(inventory, 19));
    }

    @Test
    void pagedTabContentUsesFooterArrowsForLargeTabs() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());

        runtime.open(player, pagedTabGalleryMenu());
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Profile Item 0", slotTitle(inventory, 19));
        assertEquals("Next Page", slotTitle(inventory, 53));

        InventoryPreClickEvent nextPage = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(nextPage);

        assertTrue(nextPage.isCancelled());
        assertEquals("Previous Page", slotTitle(inventory, 45));
        assertEquals("Profile Item 21", slotTitle(inventory, 19));
        assertEquals("Profile Item 28", slotTitle(inventory, 28));
    }

    @Test
    void canvasRoutesPlacedItemsThroughOwnedInventoryIdentity() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer());
        AtomicBoolean opened = new AtomicBoolean(false);

        runtime.open(player, canvasMenu(opened));
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Museum Rewards", slotTitle(inventory, 13));

        InventoryPreClickEvent open = new InventoryPreClickEvent(inventory, player, new Click.Left(13));
        runtime.onInventoryPreClick(open);

        assertTrue(open.isCancelled());
        assertTrue(opened.get());
    }

    private static Menu pagedMenu() {
        return new StandardMenuService().list()
                .title("Profiles")
                .back(context -> { })
                .addItems(IntStream.range(0, 73).mapToObj(i -> MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Item " + i)
                        .action(ActionVerb.VIEW, context -> { })
                        .build()).toList())
                .build();
    }

    private static Menu toggleMenu(AtomicBoolean enabled) {
        return new StandardMenuService().list()
                .title("Toggle")
                .addItem(MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name(enabled.get() ? "Enabled" : "Disabled")
                        .action(ActionVerb.TOGGLE, context -> {
                            enabled.set(!enabled.get());
                            context.open(toggleMenu(enabled));
                        })
                        .build())
                .build();
    }

    private static Menu galleryMenu(AtomicBoolean backed) {
        return new StandardMenuService().tabs()
                .title("Gallery")
                .back(context -> backed.set(true))
                .defaultTab("profiles")
                .addTab(MenuTab.of("profiles", "Profiles", MenuIcon.vanilla("player_head"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("player_head"))
                                .name("Your SkyBlock Profile")
                                .action(ActionVerb.VIEW, context -> { })
                                .build(),
                        MenuButton.builder(MenuIcon.vanilla("gray_dye"))
                                .name("Profile Slot #5")
                                .action(ActionVerb.OPEN, context -> { })
                                .build()
                )))
                .addTab(MenuTab.of("progress", "Progress", MenuIcon.vanilla("experience_bottle"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                                .name("Farming XLIX")
                                .action(ActionVerb.VIEW, context -> { })
                                .build(),
                        MenuButton.builder(MenuIcon.vanilla("book"))
                                .name("Museum Rewards")
                                .action(ActionVerb.VIEW, context -> { })
                                .build()
                )))
                .build();
    }

    private static Menu overflowGalleryMenu() {
        StandardMenuService menus = new StandardMenuService();
        var builder = menus.tabs()
                .title("Overflow")
                .defaultTab("tab-0");
        for (int i = 0; i < 10; i++) {
            int index = i;
            builder.addTab(MenuTab.of("tab-" + index, "Tab " + index, MenuIcon.vanilla("stone"), List.of(
                    MenuButton.builder(MenuIcon.vanilla("stone"))
                            .name("Tab " + index + " Item 0")
                            .action(ActionVerb.VIEW, context -> { })
                            .build()
            )));
        }
        return builder.build();
    }

    private static Menu pagedTabGalleryMenu() {
        return new StandardMenuService().tabs()
                .title("Paged Tabs")
                .defaultTab("profiles")
                .addTab(MenuTab.of("profiles", "Profiles", MenuIcon.vanilla("player_head"),
                        IntStream.range(0, 29)
                                .mapToObj(i -> MenuButton.builder(MenuIcon.vanilla("player_head"))
                                        .name("Profile Item " + i)
                                        .action(ActionVerb.VIEW, context -> { })
                                        .build())
                                .toList()))
                .addTab(MenuTab.of("progress", "Progress", MenuIcon.vanilla("experience_bottle"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                                .name("Farming XLIX")
                                .action(ActionVerb.VIEW, context -> { })
                                .build()
                )))
                .build();
    }

    private static Menu canvasMenu(AtomicBoolean opened) {
        return new StandardMenuService().canvas()
                .title("Canvas")
                .place(13, MenuButton.builder(MenuIcon.vanilla("book"))
                        .name("Museum Rewards")
                        .action(ActionVerb.VIEW, context -> opened.set(true))
                        .build())
                .build();
    }

    private static TestPlayer player() {
        return new TestPlayer(UUID.randomUUID());
    }

    private static String slotTitle(Inventory inventory, int slot) {
        return flatten(inventory.getItemStack(slot).get(DataComponents.CUSTOM_NAME));
    }

    private static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            append(builder, child);
        }
    }

    private static final class TestPlayer extends Player {

        private final List<Inventory> openedInventories = new ArrayList<>();
        private int closeCount;

        private TestPlayer(UUID uuid) {
            super(new TestPlayerConnection(), new GameProfile(uuid, "menu-test"));
        }

        @Override
        public boolean openInventory(Inventory inventory) {
            openedInventories.add(inventory);
            return true;
        }

        @Override
        public void closeInventory() {
            closeCount++;
        }

        private Inventory lastOpenedInventory() {
            return openedInventories.getLast();
        }

        private int openCount() {
            return openedInventories.size();
        }

        private int closeCount() {
            return closeCount;
        }
    }

    private static final class TestPlayerConnection extends PlayerConnection {

        @Override
        public void sendPacket(SendablePacket packet) {
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }
    }
}
