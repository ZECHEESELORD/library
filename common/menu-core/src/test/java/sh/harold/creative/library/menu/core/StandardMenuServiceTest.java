package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuPair;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.MenuTraceController;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.sound.SoundCueKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardMenuServiceTest {

    private final StandardMenuService menus = new StandardMenuService();

    @Test
    void listMenuUsesStableHouseFooterGrammar() {
        Menu menu = menus.list()
                .title("Profiles")
                .addItems(sampleButtons("Item", 73))
                .build();

        MenuFrame firstPage = menu.frame("page:0");
        MenuFrame middlePage = menu.frame("page:1");

        assertEquals("Profiles (1/3)", ComponentText.flatten(firstPage.title()));
        assertEquals("Profiles (2/3)", ComponentText.flatten(middlePage.title()));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(firstPage, 9));
        assertEquals("Item Item 0", titleAt(firstPage, 10));
        assertEquals("Item Item 27", titleAt(firstPage, 43));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(firstPage, 44));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(firstPage, 48));
        assertEquals("Close", titleAt(firstPage, 49));
        assertEquals(NamedTextColor.RED, titleColorAt(firstPage, 49));
        assertEquals(List.of(), loreAt(firstPage, 49));
        assertEquals("Next Page", titleAt(firstPage, 53));
        assertEquals(NamedTextColor.GREEN, titleColorAt(firstPage, 53));
        assertEquals(List.of("Page 2"), loreAt(firstPage, 53));

        assertEquals("Previous Page", titleAt(middlePage, 45));
        assertEquals(NamedTextColor.GREEN, titleColorAt(middlePage, 45));
        assertEquals(List.of("Page 1"), loreAt(middlePage, 45));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(middlePage, 48));
        assertEquals("Close", titleAt(middlePage, 49));
        assertEquals("Next Page", titleAt(middlePage, 53));
        assertEquals(List.of("Page 3"), loreAt(middlePage, 53));
    }

    @Test
    void singlePagePureListKeepsUnsuffixedTitle() {
        Menu menu = menus.list()
                .title("Profiles")
                .addItems(sampleButtons("Item", 1))
                .build();

        MenuFrame frame = menu.initialFrame();
        assertEquals("Profiles", ComponentText.flatten(frame.title()));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 9));
        assertEquals("Item Item 0", titleAt(frame, 10));
        assertEquals("minecraft:air", iconAt(frame, 11));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 17));
    }

    @Test
    void tabsCenterGroupsAndRenderHighlightChrome() {
        Menu menu = menus.tabs()
                .title("Blocks")
                .defaultTab("oak")
                .addGroup(MenuTabGroup.of("wood", List.of(
                        listTab("oak", "Oak", 1),
                        listTab("acacia", "Acacia", 1),
                        listTab("cedar", "Cedar", 1),
                        listTab("dark-oak", "Dark Oak", 1)
                )))
                .addGroup(MenuTabGroup.of("stone", List.of(
                        listTab("stone", "Stone", 1),
                        listTab("cobble", "Cobblestone", 1)
                )))
                .build();

        MenuFrame frame = menu.initialFrame();

        assertEquals("Oak", titleAt(frame, 1));
        assertEquals("Acacia", titleAt(frame, 2));
        assertEquals("Cedar", titleAt(frame, 3));
        assertEquals("Dark Oak", titleAt(frame, 4));
        assertEquals("Stone", titleAt(frame, 6));
        assertEquals("Cobblestone", titleAt(frame, 7));
        assertEquals(List.of("", "Oak", "", "CLICK to view!"), loreAt(frame, 1));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 5));

        assertEquals("minecraft:lime_stained_glass_pane", iconAt(frame, 10));
        assertTrue(glowAt(frame, 10));
        assertEquals("minecraft:gray_stained_glass_pane", iconAt(frame, 11));
        assertFalse(glowAt(frame, 11));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 14));

        assertEquals("Oak Item 0", titleAt(frame, 19));
        assertEquals("minecraft:air", iconAt(frame, 20));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 18));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 48));
        assertEquals("Close", titleAt(frame, 49));
    }

    @Test
    void tabsCanUseAuthoredRepresentativeItemSummaries() {
        Menu menu = menus.tabs()
                .title("Modes")
                .defaultTab("upgrades")
                .addTab(MenuTab.builder("upgrades", MenuIcon.vanilla("nether_star"))
                        .name(Component.text("Account & Profile Upgrades", NamedTextColor.LIGHT_PURPLE))
                        .description("Upgrade your account and profile unlocks from one place.")
                        .pairs(
                                MenuPair.of("Profile", "Nothing Going On..."),
                                MenuPair.of("Account", "Bazaar Flipper II"))
                        .items(sampleButtons("Upgrade", 1))
                        .build())
                .addTab(MenuTab.of("mail", "Mail", MenuIcon.vanilla("book"), sampleButtons("Mail", 1)))
                .build();

        MenuFrame frame = menu.initialFrame();
        int slot = slotWithTitle(frame, "Account & Profile Upgrades");
        List<String> lore = loreAt(frame, slot);

        assertEquals(NamedTextColor.LIGHT_PURPLE, frame.slots().get(slot).title().color());
        assertTrue(lore.getFirst().startsWith("Upgrade your account"));
        assertTrue(lore.contains("Profile: Nothing Going On..."));
        assertTrue(lore.contains("Account: Bazaar Flipper II"));
        assertEquals("CLICK to view!", lore.getLast());
    }

    @Test
    void tabsOverflowUseStandardNavArrowsAndScrollByWindowStart() {
        Menu menu = menus.tabs()
                .title("Many Tabs")
                .defaultTab("tab-0")
                .addGroup(MenuTabGroup.of("all", IntStream.range(0, 10)
                        .mapToObj(i -> listTab("tab-" + i, "Tab " + i, 1))
                        .toList()))
                .build();

        MenuFrame initial = menu.frame("tab:tab-0:nav:0:page:0");
        MenuFrame scrolled = menu.frame("tab:tab-0:nav:1:page:0");
        MenuFrame end = menu.frame("tab:tab-0:nav:3:page:0");

        assertEquals("Previous Tab", titleAt(initial, 0));
        assertEquals(NamedTextColor.GREEN, titleColorAt(initial, 0));
        assertEquals(List.of("Page 1"), loreAt(initial, 0));
        assertTrue(interactionAt(initial, 0, MenuClick.LEFT) == null);
        assertEquals("Next Tab", titleAt(initial, 8));
        assertEquals(NamedTextColor.GREEN, titleColorAt(initial, 8));
        assertEquals(List.of("Page 2"), loreAt(initial, 8));
        assertTrue(interactionAt(initial, 8, MenuClick.LEFT) != null);
        assertTrue(interactionAt(initial, 8, MenuClick.RIGHT) != null);
        assertEquals(SoundCueKeys.MENU_SCROLL, interactionAt(initial, 8, MenuClick.LEFT).soundCueKey());
        assertEquals(SoundCueKeys.MENU_SCROLL, interactionAt(initial, 8, MenuClick.RIGHT).soundCueKey());
        assertEquals("Tab 0", titleAt(initial, 1));
        assertEquals("Tab 6", titleAt(initial, 7));
        assertEquals("Tab 0 Item 0", titleAt(scrolled, 19));
        assertEquals("Tab 1", titleAt(scrolled, 1));
        assertEquals("Tab 7", titleAt(scrolled, 7));
        assertEquals("Tab 3", titleAt(end, 1));
        assertEquals("Tab 9", titleAt(end, 7));
    }

    @Test
    void tabsListContentPagesInsideThreeBySevenPanelAndUsesFooterPagingForLargeTabs() {
        Menu menu = menus.tabs()
                .title("Paged Tabs")
                .defaultTab("alpha")
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), sampleButtons("Alpha", 29)))
                .addTab(MenuTab.of("beta", "Beta", MenuIcon.vanilla("diamond"), sampleButtons("Beta", 1)))
                .build();

        MenuFrame firstPage = menu.frame("tab:alpha:nav:0:page:0");
        MenuFrame secondPage = menu.frame("tab:alpha:nav:0:page:1");

        assertEquals("Alpha Item 0", titleAt(firstPage, 19));
        assertEquals("Alpha Item 20", titleAt(firstPage, 43));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(firstPage, 18));
        assertEquals("Next Page", titleAt(firstPage, 53));
        assertEquals(NamedTextColor.GREEN, titleColorAt(firstPage, 53));
        assertEquals(List.of("Page 2"), loreAt(firstPage, 53));
        assertEquals("Alpha Item 21", titleAt(secondPage, 19));
        assertEquals("Alpha Item 28", titleAt(secondPage, 28));
        assertEquals("Previous Page", titleAt(secondPage, 45));
        assertEquals(NamedTextColor.GREEN, titleColorAt(secondPage, 45));
        assertEquals(List.of("Page 1"), loreAt(secondPage, 45));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(secondPage, 48));
        assertEquals(SoundCueKeys.MENU_SCROLL, interactionAt(firstPage, 53, MenuClick.LEFT).soundCueKey());
        assertEquals(SoundCueKeys.MENU_SCROLL, interactionAt(secondPage, 45, MenuClick.LEFT).soundCueKey());
    }

    @Test
    void menuButtonInteractionsDefaultAndOverrideTheirSoundCues() {
        MenuButton ordinary = MenuButton.builder(MenuIcon.vanilla("stone"))
                .name("Ordinary")
                .action(ActionVerb.VIEW, context -> { })
                .build();
        MenuButton claim = MenuButton.builder(MenuIcon.vanilla("chest"))
                .name("Claim")
                .action(ActionVerb.CLAIM, context -> { })
                .build();
        MenuButton denied = MenuButton.builder(MenuIcon.vanilla("gray_dye"))
                .name("Denied")
                .action(ActionVerb.OPEN, context -> { })
                .sound(SoundCueKeys.RESULT_DENY)
                .build();

        assertEquals(SoundCueKeys.MENU_CLICK, ordinary.interactions().get(MenuClick.LEFT).soundCueKey());
        assertEquals(SoundCueKeys.RESULT_CONFIRM, claim.interactions().get(MenuClick.LEFT).soundCueKey());
        assertEquals(SoundCueKeys.RESULT_DENY, denied.interactions().get(MenuClick.LEFT).soundCueKey());
    }

    @Test
    void tabsCanvasContentUseBlackPaneFillerByDefault() {
        Menu menu = menus.tabs()
                .title("Canvas Tabs")
                .defaultTab("alpha")
                .addTab(new MenuTab("alpha", Component.text("Alpha"), MenuIcon.vanilla("book"),
                        MenuTabContent.canvas(builder -> builder.place(31, MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                                .name("Centered Card")
                                .build()))))
                .addTab(canvasTab("beta", "Beta", 43, "Bottom Corner"))
                .build();

        MenuFrame frame = menu.initialFrame();

        assertEquals("Centered Card", titleAt(frame, 31));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 20));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 48));
        assertEquals("Close", titleAt(frame, 49));
    }

    @Test
    void tabsCanvasContentCanDisableBlackPaneFiller() {
        Menu menu = menus.tabs()
                .title("Canvas Tabs")
                .defaultTab("alpha")
                .addTab(new MenuTab("alpha", Component.text("Alpha"), MenuIcon.vanilla("book"),
                        MenuTabContent.canvas(builder -> builder
                                .fillWithBlackPane(false)
                                .place(19, MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                                        .name("Canvas Card")
                                        .build()))))
                .addTab(canvasTab("beta", "Beta", 43, "Bottom Corner"))
                .build();

        MenuFrame frame = menu.initialFrame();

        assertEquals("Canvas Card", titleAt(frame, 19));
        assertEquals("minecraft:air", iconAt(frame, 20));
        assertEquals("Close", titleAt(frame, 49));
    }

    @Test
    void sessionStateShowsAutoBackForChildMenus() {
        Menu root = menus.list()
                .title("Gallery")
                .addItems(sampleButtons("Root", 1))
                .build();
        Menu child = menus.canvas()
                .title("Museum Rewards Preview")
                .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                        .name("Museum Rewards")
                        .build())
                .build();

        MenuSessionState state = new MenuSessionState(root);
        state.openChild(child);

        MenuSlot back = state.currentFrame().slots().get(48);
        MenuSlot close = state.currentFrame().slots().get(49);

        assertEquals("Go Back", ComponentText.flatten(back.title()));
        assertEquals(NamedTextColor.GREEN, back.title().color());
        assertEquals(List.of("To Gallery"), loreAt(back));
        assertEquals("Close", ComponentText.flatten(close.title()));
        assertEquals(NamedTextColor.RED, close.title().color());
        assertEquals(List.of(), loreAt(close));
    }

    @Test
    void sessionStateTracksFrameHistoryButBackLoreUsesMenuTitleOnly() {
        Menu root = menus.list()
                .title("Gallery")
                .addItems(sampleButtons("Root", 1))
                .build();
        Menu child = menus.list()
                .title("Profiles")
                .addItems(sampleButtons("Item", 73))
                .build();

        MenuSessionState state = new MenuSessionState(root);
        state.openChild(child);
        state.openFrame("page:1");

        assertEquals("Go Back", titleAt(state.currentFrame(), 48));
        assertEquals(List.of("To Profiles"), loreAt(state.currentFrame().slots().get(48)));

        assertTrue(state.back());
        assertEquals("page:0", state.frameId());
        assertEquals(List.of("To Gallery"), loreAt(state.currentFrame().slots().get(48)));
    }

    @Test
    void compiledMenusResolveFramesLazilyAndCachePerFrame() {
        AtomicInteger resolutions = new AtomicInteger();
        Menu menu = new StandardMenu(
                Component.text("Lazy"),
                MenuGeometry.LIST,
                6,
                "page:0",
                Set.of("page:0", "page:1"),
                frameId -> {
                    resolutions.incrementAndGet();
                    return new MenuFrame(Component.text(frameId), List.of());
                });

        assertEquals(0, resolutions.get());
        assertEquals("page:0", ComponentText.flatten(menu.frame("page:0").title()));
        assertEquals(1, resolutions.get());
        assertEquals("page:0", ComponentText.flatten(menu.frame("page:0").title()));
        assertEquals(1, resolutions.get());
        assertEquals("page:1", ComponentText.flatten(menu.frame("page:1").title()));
        assertEquals(2, resolutions.get());
    }

    @Test
    void reactiveMenusKeepSessionStateIsolatedAndPatchRenderedSlots() {
        ReactiveMenu menu = menus.reactive()
                .stateFactory(() -> new ToggleState(false))
                .render(state -> ReactiveMenuView.builder("Reactive Toggle")
                        .place(13, MenuButton.builder(MenuIcon.vanilla("lever"))
                                .name(state.enabled() ? "Canvas Filler: On" : "Canvas Filler: Off")
                                .description("Toggle the filler state without reopening the menu.")
                                .emit(ActionVerb.TOGGLE, "toggle-filler")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click && "toggle-filler".equals(click.message())) {
                        return ReactiveMenuResult.stay(new ToggleState(!state.enabled()));
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();

        MenuSessionState first = new MenuSessionState(menu);
        MenuSessionState second = new MenuSessionState(menu);

        assertEquals("Canvas Filler: Off", titleAt(first.currentFrame(), 13));
        assertEquals("Canvas Filler: Off", titleAt(second.currentFrame(), 13));

        first.dispatchReactive(new ReactiveMenuInput.Click(13, MenuClick.LEFT, false, "toggle-filler"));

        assertEquals("Canvas Filler: On", titleAt(first.currentFrame(), 13));
        assertEquals("Canvas Filler: Off", titleAt(second.currentFrame(), 13));
    }

    @Test
    void reactiveSessionsCacheCompiledPlacementsByItemIdentityAcrossRerenders() {
        MenuDisplayItem staticCard = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Shift Or Drag")
                .description("Shift-click a stack from the bottom inventory, or click one to load the reactive cursor and place it in the center slot.")
                .build();

        ReactiveMenu menu = menus.reactive()
                .stateFactory(() -> new ToggleState(false))
                .render(state -> ReactiveMenuView.builder("Reactive Cache")
                        .place(20, staticCard)
                        .place(24, MenuDisplayItem.builder(MenuIcon.vanilla("lever"))
                                .name(state.enabled() ? "Enabled" : "Disabled")
                                .build())
                        .build())
                .reduce((state, input) -> input instanceof ReactiveMenuInput.Tick
                        ? ReactiveMenuResult.stay(new ToggleState(!state.enabled()))
                        : ReactiveMenuResult.stay(state))
                .build();

        MenuSessionState state = new MenuSessionState(menu);
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();

        List<String> firstLogs = new ArrayList<>();
        MenuTrace.withTrace(trace, firstLogs::add, "test", UUID.randomUUID(), "refresh", () -> state.currentFrame());
        String firstSummary = summaryLine(firstLogs, "refresh");
        assertTrue(firstSummary.contains("placementCompileHits=\"0\""));
        assertTrue(firstSummary.contains("placementCompileMisses=\"2\""));

        state.dispatchReactive(new ReactiveMenuInput.Tick(1L));

        List<String> secondLogs = new ArrayList<>();
        MenuTrace.withTrace(trace, secondLogs::add, "test", UUID.randomUUID(), "refresh", () -> state.currentFrame());
        String secondSummary = summaryLine(secondLogs, "refresh");
        assertTrue(secondSummary.contains("placementCompileHits=\"1\""));
        assertTrue(secondSummary.contains("placementCompileMisses=\"1\""));
    }

    @Test
    void tabsCustomFooterAllowCanvasContentInRowFive() {
        Menu menu = menus.tabs()
                .title("Canvas Tabs")
                .customFooter()
                .defaultTab("alpha")
                .addTab(canvasTab("alpha", "Alpha", 45, "Row Five Action"))
                .addTab(canvasTab("beta", "Beta", 53, "Bottom Corner"))
                .build();

        MenuFrame frame = menu.initialFrame();

        assertEquals("Row Five Action", titleAt(frame, 45));
        assertEquals(" ", titleAt(frame, 49));
        assertTrue(interactionAt(frame, 49, MenuClick.LEFT) == null);
    }

    @Test
    void tabsCustomFooterRejectListContent() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> menus.tabs()
                .title("Mixed Tabs")
                .customFooter()
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), sampleButtons("Alpha", 1)))
                .addTab(canvasTab("beta", "Beta", 45, "Bottom Corner"))
                .build());

        assertEquals("List tab content requires the shared footer", exception.getMessage());
    }

    @Test
    void tabsRequireAtLeastTwoTabs() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> menus.tabs()
                .title("Modes")
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), sampleButtons("Alpha", 1)))
                .build());

        assertEquals("Tabs menu requires at least two tabs", exception.getMessage());
    }

    @Test
    void tabsValidateDefaultTabId() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> menus.tabs()
                .title("Modes")
                .defaultTab("missing")
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), sampleButtons("Alpha", 1)))
                .addTab(MenuTab.of("beta", "Beta", MenuIcon.vanilla("diamond"), sampleButtons("Beta", 1)))
                .build());

        assertEquals("Default tab id does not exist: missing", exception.getMessage());
    }

    @Test
    void canvasRejectsPlacedItemsAtReservedBackSlot() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> menus.canvas()
                .title("Canvas")
                .place(48, MenuDisplayItem.builder(MenuIcon.vanilla("compass"))
                        .name("Reserved")
                        .build())
                .build());

        assertEquals("Placed items may not overwrite reserved canvas chrome slots", exception.getMessage());
    }

    @Test
    void pairsRejectOddAlternatingInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .pairs("Cost", "2,750 Gems", "You have")
                .build());

        assertEquals("rawPairs must contain an even number of entries", exception.getMessage());
    }

    private static MenuTab listTab(String id, String name, int count) {
        return MenuTab.of(id, name, MenuIcon.vanilla("stone"), sampleButtons(name, count));
    }

    private static MenuTab canvasTab(String id, String name, int slot, String itemName) {
        return new MenuTab(id, Component.text(name), MenuIcon.vanilla("book"),
                MenuTabContent.canvas(builder -> builder.place(slot, MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                        .name(itemName)
                        .build())));
    }

    private static List<MenuItem> sampleButtons(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (MenuItem) MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name(prefix + " Item " + i)
                        .action(ActionVerb.VIEW, context -> {})
                        .build())
                .toList();
    }

    private static String titleAt(MenuFrame frame, int slot) {
        return ComponentText.flatten(frame.slots().get(slot).title());
    }

    private static NamedTextColor titleColorAt(MenuFrame frame, int slot) {
        return (NamedTextColor) frame.slots().get(slot).title().color();
    }

    private static String iconAt(MenuFrame frame, int slot) {
        return frame.slots().get(slot).icon().key();
    }

    private static int slotWithTitle(MenuFrame frame, String title) {
        return IntStream.range(0, frame.slots().size())
                .filter(slot -> titleAt(frame, slot).equals(title))
                .findFirst()
                .orElseThrow();
    }

    private static boolean glowAt(MenuFrame frame, int slot) {
        return frame.slots().get(slot).glow();
    }

    private static MenuInteraction interactionAt(MenuFrame frame, int slot, MenuClick click) {
        return frame.slots().get(slot).interactions().get(click);
    }

    private static List<String> loreAt(MenuFrame frame, int slot) {
        return loreAt(frame.slots().get(slot));
    }

    private static List<String> loreAt(MenuSlot slot) {
        return slot.lore().stream()
                .map(ComponentText::flatten)
                .toList();
    }

    private static String summaryLine(List<String> logs, String cause) {
        return logs.stream()
                .filter(line -> line.startsWith("summary "))
                .filter(line -> line.contains("cause=\"" + cause + "\""))
                .findFirst()
                .orElseThrow();
    }

    private record ToggleState(boolean enabled) {
    }
}
