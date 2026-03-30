package sh.harold.creative.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.List;
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
                .back(context -> {})
                .addItems(sampleButtons("Item", 73))
                .build();

        MenuFrame firstPage = menu.frames().get("page:0");
        MenuFrame middlePage = menu.frames().get("page:1");

        assertEquals("Back", titleAt(firstPage, 45));
        assertEquals("Close", titleAt(firstPage, 49));
        assertEquals("Next Page", titleAt(firstPage, 53));

        assertEquals("Previous Page", titleAt(middlePage, 45));
        assertEquals("Back", titleAt(middlePage, 46));
        assertEquals("Close", titleAt(middlePage, 49));
        assertEquals("Next Page", titleAt(middlePage, 53));
    }

    @Test
    void tabsCenterGroupsAndRenderHighlightChrome() {
        Menu menu = menus.tabs()
                .title("Blocks")
                .back(context -> {})
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
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 5));

        assertEquals("minecraft:lime_stained_glass_pane", iconAt(frame, 10));
        assertTrue(glowAt(frame, 10));
        assertEquals("minecraft:gray_stained_glass_pane", iconAt(frame, 11));
        assertFalse(glowAt(frame, 11));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 14));

        assertEquals("Oak Item 0", titleAt(frame, 19));
        assertEquals("minecraft:air", iconAt(frame, 20));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 18));
        assertEquals("Back", titleAt(frame, 45));
        assertEquals("Close", titleAt(frame, 49));
    }

    @Test
    void tabsOverflowUseNavArrowsAndScrollByWindowStart() {
        Menu menu = menus.tabs()
                .title("Many Tabs")
                .defaultTab("tab-0")
                .addGroup(MenuTabGroup.of("all", IntStream.range(0, 10)
                        .mapToObj(i -> listTab("tab-" + i, "Tab " + i, 1))
                        .toList()))
                .build();

        MenuFrame initial = menu.frames().get("tab:tab-0:nav:0:page:0");
        MenuFrame scrolled = menu.frames().get("tab:tab-0:nav:1:page:0");
        MenuFrame end = menu.frames().get("tab:tab-0:nav:3:page:0");

        assertEquals("Previous Tabs", titleAt(initial, 0));
        assertTrue(interactionAt(initial, 0, MenuClick.LEFT) == null);
        assertEquals("Next Tabs", titleAt(initial, 8));
        assertTrue(interactionAt(initial, 8, MenuClick.LEFT) != null);
        assertTrue(interactionAt(initial, 8, MenuClick.RIGHT) != null);
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

        MenuFrame firstPage = menu.frames().get("tab:alpha:nav:0:page:0");
        MenuFrame secondPage = menu.frames().get("tab:alpha:nav:0:page:1");

        assertEquals("Alpha Item 0", titleAt(firstPage, 19));
        assertEquals("Alpha Item 20", titleAt(firstPage, 43));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(firstPage, 18));
        assertEquals("Next Page", titleAt(firstPage, 53));
        assertEquals("Alpha Item 21", titleAt(secondPage, 19));
        assertEquals("Alpha Item 28", titleAt(secondPage, 28));
        assertEquals("Previous Page", titleAt(secondPage, 45));
    }

    @Test
    void tabsCanvasContentUseBlackPaneFillerByDefault() {
        Menu menu = menus.tabs()
                .title("Canvas Tabs")
                .defaultTab("alpha")
                .addTab(new MenuTab("alpha", net.kyori.adventure.text.Component.text("Alpha"), MenuIcon.vanilla("book"),
                        MenuTabContent.canvas(builder -> builder.place(31, MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                                .name("Centered Card")
                                .build()))))
                .addTab(canvasTab("beta", "Beta", 43, "Bottom Corner"))
                .build();

        MenuFrame frame = menu.initialFrame();

        assertEquals("Centered Card", titleAt(frame, 31));
        assertEquals("minecraft:black_stained_glass_pane", iconAt(frame, 20));
        assertEquals("Close", titleAt(frame, 49));
    }

    @Test
    void tabsCanvasContentCanDisableBlackPaneFiller() {
        Menu menu = menus.tabs()
                .title("Canvas Tabs")
                .defaultTab("alpha")
                .addTab(new MenuTab("alpha", net.kyori.adventure.text.Component.text("Alpha"), MenuIcon.vanilla("book"),
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
    void tabsCustomFooterRejectSharedFooterControls() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> menus.tabs()
                .title("Canvas Tabs")
                .customFooter()
                .back(context -> {})
                .addTab(canvasTab("alpha", "Alpha", 45, "Row Five Action"))
                .addTab(canvasTab("beta", "Beta", 53, "Bottom Corner"))
                .build());

        assertEquals("Custom-footer tabs may not use back(...)", exception.getMessage());
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
    void listRejectsUtilityCollisionsWithReservedChrome() {
        MenuItem utility = MenuDisplayItem.builder(MenuIcon.vanilla("compass"))
                .name("Utility")
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> menus.list()
                .title("Profiles")
                .back(context -> {})
                .utility(UtilitySlot.LEFT_1, utility)
                .addItems(sampleButtons("Item", 73))
                .build());

        assertTrue(exception.getMessage().contains("reserved house chrome"));
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
        return new MenuTab(id, net.kyori.adventure.text.Component.text(name), MenuIcon.vanilla("book"),
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

    private static String iconAt(MenuFrame frame, int slot) {
        return frame.slots().get(slot).icon().key();
    }

    private static boolean glowAt(MenuFrame frame, int slot) {
        return frame.slots().get(slot).glow();
    }

    private static MenuInteraction interactionAt(MenuFrame frame, int slot, MenuClick click) {
        return frame.slots().get(slot).interactions().get(click);
    }
}
