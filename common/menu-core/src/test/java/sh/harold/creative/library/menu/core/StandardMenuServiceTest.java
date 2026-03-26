package sh.harold.creative.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardMenuServiceTest {

    private final StandardMenuService menus = new StandardMenuService();

    @Test
    void listMenuUsesStableHouseFooterGrammar() {
        Menu menu = menus.list()
                .title("Profiles")
                .back(context -> {})
                .addItems(sampleButtons(73))
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
    void tabsUseTopRowStripAndStableFooterGrammar() {
        Menu menu = menus.tabs()
                .title("Modes")
                .back(context -> {})
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), sampleButtons(3)))
                .addTab(MenuTab.of("beta", "Beta", MenuIcon.vanilla("diamond"), sampleButtons(2)))
                .build();

        MenuFrame frame = menu.initialFrame();

        assertTrue(titleAt(frame, 0).contains("Alpha"));
        assertTrue(titleAt(frame, 1).contains("Beta"));
        assertEquals("Back", titleAt(frame, 45));
        assertEquals("Close", titleAt(frame, 49));
    }

    @Test
    void tabsRequireAtLeastTwoTabs() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> menus.tabs()
                .title("Modes")
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), sampleButtons(3)))
                .build());

        assertEquals("Tabs menu requires at least two tabs", exception.getMessage());
    }

    @Test
    void tabsValidateDefaultTabId() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> menus.tabs()
                .title("Modes")
                .defaultTab("missing")
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), sampleButtons(3)))
                .addTab(MenuTab.of("beta", "Beta", MenuIcon.vanilla("diamond"), sampleButtons(2)))
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
                .addItems(sampleButtons(73))
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

    private static List<MenuItem> sampleButtons(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (MenuItem) MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Item " + i)
                        .action(ActionVerb.VIEW, context -> {})
                        .build())
                .toList();
    }

    private static String titleAt(MenuFrame frame, int slot) {
        return ComponentText.flatten(frame.slots().get(slot).title());
    }
}
