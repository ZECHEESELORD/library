package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.core.StandardMenuService;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.SoundCuePacks;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.core.StandardSoundCueRegistry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperMenuRuntimeTest {

    private static final Key SPECIAL_SOUND = Key.key("test", "menu/special");

    @Test
    void openClickNavigateAndCloseUsesOwnedInventoryIdentity() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        Menu menu = pagedMenu();

        runtime.open(player, menu);

        Inventory inventory = access.lastOpenedInventory();
        assertEquals("Profiles (1/3)", inventoryTitle(access, inventory));
        assertEquals("Close", slotTitle(access, inventory, 49));
        assertEquals("Next Page", slotTitle(access, inventory, 53));
        assertEquals(List.of("Page 2"), slotLore(access, inventory, 53));

        InventoryClickEvent nextPage = click(player, inventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(nextPage);

        assertTrue(nextPage.isCancelled());
        Inventory secondPageInventory = access.lastOpenedInventory();
        assertNotSame(inventory, secondPageInventory);
        assertEquals("Profiles (2/3)", inventoryTitle(access, secondPageInventory));
        assertEquals("Previous Page", slotTitle(access, secondPageInventory, 45));
        assertEquals(List.of("Page 1"), slotLore(access, secondPageInventory, 45));
        assertEquals("Close", slotTitle(access, secondPageInventory, 49));
        assertEquals("Next Page", slotTitle(access, secondPageInventory, 53));
        assertEquals(List.of("Page 3"), slotLore(access, secondPageInventory, 53));

        InventoryClickEvent close = click(player, secondPageInventory, 49, ClickType.LEFT);
        runtime.onInventoryClick(close);

        assertTrue(close.isCancelled());
        assertEquals(List.of(viewerId), access.closedPlayers);
    }

    @Test
    void actionCanReplaceCurrentMenuAndRefreshRenderedContents() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, toggleMenu(enabled));
        Inventory inventory = access.lastOpenedInventory();
        assertEquals("Disabled", slotTitle(access, inventory, 10));

        runtime.onInventoryClick(click(player, inventory, 10, ClickType.LEFT));

        assertEquals("Enabled", slotTitle(access, inventory, 10));
        assertEquals(1, access.openedInventories.size());
    }

    @Test
    void closeAndSpoofedInventoriesDoNotRouteByTitle() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        Menu menu = pagedMenu();

        runtime.open(player, menu);
        Inventory ownedInventory = access.lastOpenedInventory();

        Inventory spoofedInventory = access.createInventory(new InventoryHolder() {
            @Override
            public Inventory getInventory() {
                return null;
            }
        }, 54, Component.text("Profiles"));

        InventoryClickEvent spoofedClick = click(player, spoofedInventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(spoofedClick);
        assertFalse(spoofedClick.isCancelled());
        assertTrue(access.closedPlayers.isEmpty());

        runtime.onInventoryClose(new InventoryCloseEvent(view(player, ownedInventory)));

        InventoryClickEvent staleClick = click(player, ownedInventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(staleClick);
        assertFalse(staleClick.isCancelled());
    }

    @Test
    void disconnectCleansUpViewerSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, pagedMenu());
        runtime.onPlayerDisconnect(player);

        InventoryClickEvent click = click(player, access.lastOpenedInventory(), 53, ClickType.LEFT);
        runtime.onInventoryClick(click);
        assertFalse(click.isCancelled());
    }

    @Test
    void childBackUsesHistoryAndTabSwitchesPushFrameHistory() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, launcherMenu());
        Inventory rootInventory = access.lastOpenedInventory();
        assertEquals("Open Gallery", slotTitle(access, rootInventory, 10));

        InventoryClickEvent openChild = click(player, rootInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(openChild);

        assertTrue(openChild.isCancelled());
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Go Back", slotTitle(access, inventory, 48));
        assertEquals("Profiles", slotTitle(access, inventory, 3));
        assertEquals("Progress", slotTitle(access, inventory, 4));
        assertEquals("Your SkyBlock Profile", slotTitle(access, inventory, 19));

        InventoryClickEvent switchTab = click(player, inventory, 4, ClickType.LEFT);
        runtime.onInventoryClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Profiles", slotTitle(access, inventory, 3));
        assertEquals("Progress", slotTitle(access, inventory, 4));
        assertEquals("Farming XLIX", slotTitle(access, inventory, 19));

        InventoryClickEvent backToPreviousFrame = click(player, inventory, 48, ClickType.LEFT);
        runtime.onInventoryClick(backToPreviousFrame);

        assertTrue(backToPreviousFrame.isCancelled());
        Inventory afterFrameBack = access.lastOpenedInventory();
        assertEquals("Go Back", slotTitle(access, afterFrameBack, 48));
        assertEquals("Your SkyBlock Profile", slotTitle(access, afterFrameBack, 19));

        InventoryClickEvent backToRoot = click(player, afterFrameBack, 48, ClickType.LEFT);
        runtime.onInventoryClick(backToRoot);

        assertTrue(backToRoot.isCancelled());
        Inventory finalInventory = access.lastOpenedInventory();
        assertEquals("Open Gallery", slotTitle(access, finalInventory, 10));
    }

    @Test
    void navArrowsScrollStripWithoutChangingActiveContent() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, overflowGalleryMenu());
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Previous Tab", slotTitle(access, inventory, 0));
        assertEquals(List.of("Page 1"), slotLore(access, inventory, 0));
        assertEquals("Next Tab", slotTitle(access, inventory, 8));
        assertEquals(List.of("Page 2"), slotLore(access, inventory, 8));
        assertEquals("Tab 0", slotTitle(access, inventory, 1));
        assertEquals("Tab 6", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent scrollRight = click(player, inventory, 8, ClickType.LEFT);
        runtime.onInventoryClick(scrollRight);

        assertTrue(scrollRight.isCancelled());
        assertEquals("Tab 1", slotTitle(access, inventory, 1));
        assertEquals("Tab 7", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent jumpEnd = click(player, inventory, 8, ClickType.RIGHT);
        runtime.onInventoryClick(jumpEnd);

        assertTrue(jumpEnd.isCancelled());
        assertEquals("Tab 3", slotTitle(access, inventory, 1));
        assertEquals("Tab 9", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent switchTab = click(player, inventory, 7, ClickType.LEFT);
        runtime.onInventoryClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Tab 9 Item 0", slotTitle(access, inventory, 19));
    }

    @Test
    void pagedTabContentUsesFooterArrowsForLargeTabs() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, pagedTabGalleryMenu());
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Profile Item 0", slotTitle(access, inventory, 19));
        assertEquals("Next Page", slotTitle(access, inventory, 53));
        assertEquals(List.of("Page 2"), slotLore(access, inventory, 53));

        InventoryClickEvent nextPage = click(player, inventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(nextPage);

        assertTrue(nextPage.isCancelled());
        assertEquals("Previous Page", slotTitle(access, inventory, 45));
        assertEquals(List.of("Page 1"), slotLore(access, inventory, 45));
        assertEquals("Profile Item 21", slotTitle(access, inventory, 19));
        assertEquals("Profile Item 28", slotTitle(access, inventory, 28));
    }

    @Test
    void canvasRoutesPlacedItemsThroughOwnedInventoryIdentity() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicBoolean opened = new AtomicBoolean(false);

        runtime.open(player, canvasMenu(opened));
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Museum Rewards", slotTitle(access, inventory, 13));

        InventoryClickEvent open = click(player, inventory, 13, ClickType.LEFT);
        runtime.onInventoryClick(open);

        assertTrue(open.isCancelled());
        assertTrue(opened.get());
    }

    @Test
    void interactionSoundsUseDefaultAndOverrideMappings() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        RecordingSoundCueService sounds = new RecordingSoundCueService();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), sounds);

        runtime.open(player, soundMenu());
        Inventory inventory = access.lastOpenedInventory();

        runtime.onInventoryClick(click(player, inventory, 10, ClickType.LEFT));
        runtime.onInventoryClick(click(player, inventory, 11, ClickType.LEFT));
        runtime.onInventoryClick(click(player, inventory, 12, ClickType.LEFT));

        runtime.open(player, pagedMenu());
        Inventory pagedInventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, pagedInventory, 53, ClickType.LEFT));

        assertEquals(List.of(
                SoundCueKeys.MENU_CLICK,
                SoundCueKeys.RESULT_CONFIRM,
                SPECIAL_SOUND,
                SoundCueKeys.MENU_SCROLL
        ), sounds.playedKeys());
    }

    private static Menu pagedMenu() {
        return new StandardMenuService().list()
                .title("Profiles")
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

    private static Menu launcherMenu() {
        return new StandardMenuService().list()
                .title("Launcher")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Open Gallery")
                        .action(ActionVerb.OPEN, context -> context.open(galleryMenu()))
                        .build())
                .build();
    }

    private static Menu galleryMenu() {
        return new StandardMenuService().tabs()
                .title("Gallery")
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

    private static Menu soundMenu() {
        return new StandardMenuService().list()
                .title("Sounds")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("View Profile")
                        .action(ActionVerb.VIEW, context -> { })
                        .build())
                .addItem(MenuButton.builder(MenuIcon.vanilla("chest"))
                        .name("Claim Delivery")
                        .action(ActionVerb.CLAIM, context -> { })
                        .build())
                .addItem(MenuButton.builder(MenuIcon.vanilla("gray_dye"))
                        .name("Unavailable")
                        .action(ActionVerb.OPEN, context -> { })
                        .sound(SPECIAL_SOUND)
                        .build())
                .build();
    }

    private static Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    private static InventoryClickEvent click(Player player, Inventory inventory, int rawSlot, ClickType clickType) {
        return new InventoryClickEvent(
                view(player, inventory),
                InventoryType.SlotType.CONTAINER,
                rawSlot,
                clickType,
                InventoryAction.PICKUP_ALL);
    }

    private static InventoryView view(Player player, Inventory topInventory) {
        Inventory bottomInventory = mock(Inventory.class);
        when(bottomInventory.getSize()).thenReturn(36);
        return new TestInventoryView(topInventory, bottomInventory, player);
    }

    private static String slotTitle(TestPaperMenuAccess access, Inventory inventory, int slot) {
        ItemStack itemStack = access.model(inventory).items.get(slot);
        return flatten(itemStack.getItemMeta().displayName());
    }

    private static List<String> slotLore(TestPaperMenuAccess access, Inventory inventory, int slot) {
        ItemStack itemStack = access.model(inventory).items.get(slot);
        List<Component> lore = itemStack.getItemMeta().lore();
        if (lore == null) {
            return List.of();
        }
        return lore.stream().map(PaperMenuRuntimeTest::flatten).toList();
    }

    private static String inventoryTitle(TestPaperMenuAccess access, Inventory inventory) {
        return flatten(access.model(inventory).title());
    }

    private static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static PaperMenuSlotRenderer renderer() {
        return slot -> {
            ItemStack itemStack = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            when(itemStack.getItemMeta()).thenReturn(meta);
            when(meta.displayName()).thenReturn(slot.title());
            when(meta.lore()).thenReturn(slot.lore());
            when(meta.getEnchantmentGlintOverride()).thenReturn(slot.glow() ? Boolean.TRUE : null);
            return itemStack;
        };
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            append(builder, child);
        }
    }

    private static final class TestPaperMenuAccess implements PaperMenuAccess {

        private final Map<Inventory, InventoryModel> models = new IdentityHashMap<>();
        private final List<Inventory> openedInventories = new ArrayList<>();
        private final List<UUID> closedPlayers = new ArrayList<>();

        @Override
        public Inventory createInventory(InventoryHolder holder, int size, Component title) {
            Inventory inventory = mock(Inventory.class);
            InventoryModel model = new InventoryModel(holder, size, title);
            models.put(inventory, model);
            when(inventory.getSize()).thenReturn(size);
            when(inventory.getHolder(false)).thenReturn(holder);
            doAnswer(invocation -> {
                model.items.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(inventory).setItem(anyInt(), any());
            when(inventory.getItem(anyInt())).thenAnswer(invocation -> model.items.get(invocation.getArgument(0)));
            return inventory;
        }

        @Override
        public void openInventory(Player player, Inventory inventory) {
            openedInventories.add(inventory);
        }

        @Override
        public void closeInventory(Player player) {
            closedPlayers.add(player.getUniqueId());
        }

        Inventory lastOpenedInventory() {
            return openedInventories.getLast();
        }

        InventoryModel model(Inventory inventory) {
            return models.get(inventory);
        }
    }

    private record InventoryModel(InventoryHolder holder, int size, Component title, Map<Integer, ItemStack> items) {

        private InventoryModel(InventoryHolder holder, int size, Component title) {
            this(holder, size, title, new java.util.HashMap<>());
        }
    }

    private static final class RecordingSoundCueService implements SoundCueService {

        private final StandardSoundCueRegistry registry = new StandardSoundCueRegistry();
        private final Map<SoundCue, Key> keysByCue = new IdentityHashMap<>();
        private final List<Key> playedKeys = new ArrayList<>();

        private RecordingSoundCueService() {
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.MENU_CLICK, "test:menu_click");
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.MENU_SCROLL, "test:menu_scroll");
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.RESULT_CONFIRM, "test:result_confirm");
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.RESULT_DENY, "test:result_deny");
            register("test", SPECIAL_SOUND, "test:menu_special");
        }

        @Override
        public SoundCueRegistry registry() {
            return registry;
        }

        @Override
        public CuePlayback play(Audience audience, SoundCue cue) {
            playedKeys.add(keysByCue.get(cue));
            return CuePlayback.noop();
        }

        @Override
        public void close() {
        }

        private List<Key> playedKeys() {
            return List.copyOf(playedKeys);
        }

        private void register(String namespace, Key key, String soundKey) {
            SoundCue cue = sh.harold.creative.library.sound.SoundCues.sound(soundKey, 0.5f, 1.0f);
            keysByCue.put(cue, key);
            registry.register(SoundCuePacks.pack(namespace).cue(key, cue).build());
        }
    }

    private static final class TestInventoryView implements InventoryView {

        private final Inventory topInventory;
        private final Inventory bottomInventory;
        private final HumanEntity player;

        private TestInventoryView(Inventory topInventory, Inventory bottomInventory, HumanEntity player) {
            this.topInventory = topInventory;
            this.bottomInventory = bottomInventory;
            this.player = player;
        }

        @Override
        public Inventory getTopInventory() {
            return topInventory;
        }

        @Override
        public Inventory getBottomInventory() {
            return bottomInventory;
        }

        @Override
        public HumanEntity getPlayer() {
            return player;
        }

        @Override
        public InventoryType getType() {
            return InventoryType.CHEST;
        }

        @Override
        public void setItem(int slot, ItemStack item) {
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot < topInventory.getSize() ? topInventory.getItem(slot) : null;
        }

        @Override
        public void setCursor(ItemStack item) {
        }

        @Override
        public ItemStack getCursor() {
            return null;
        }

        @Override
        public Inventory getInventory(int slot) {
            return slot < topInventory.getSize() ? topInventory : bottomInventory;
        }

        @Override
        public int convertSlot(int rawSlot) {
            return rawSlot;
        }

        @Override
        public InventoryType.SlotType getSlotType(int slot) {
            return slot < topInventory.getSize() ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public int countSlots() {
            return topInventory.getSize() + bottomInventory.getSize();
        }

        @Override
        public boolean setProperty(Property prop, int value) {
            return false;
        }

        @Override
        public String getTitle() {
            return "Test";
        }

        @Override
        public String getOriginalTitle() {
            return "Test";
        }

        @Override
        public void setTitle(String title) {
        }

        @Override
        public org.bukkit.inventory.MenuType getMenuType() {
            return org.bukkit.inventory.MenuType.GENERIC_9X6;
        }
    }
}
