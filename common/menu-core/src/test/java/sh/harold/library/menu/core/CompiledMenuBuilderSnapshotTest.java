package sh.harold.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.CanvasMenuBuilder;
import sh.harold.library.menu.ConfirmationMenuBuilder;
import sh.harold.library.menu.ListMenuBuilder;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuTab;
import sh.harold.library.menu.TabsMenuBuilder;
import sh.harold.library.menu.UtilitySlot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompiledMenuBuilderSnapshotTest {

    private final StandardMenuService menus = new StandardMenuService();

    @Test
    void listBuildSnapshotsLazyFrameInputs() {
        ListMenuBuilder builder = menus.list()
                .title("Original List")
                .addItems(items("Item", 29))
                .utility(UtilitySlot.RIGHT_1, display("Original Utility"));

        Menu menu = builder.build();

        builder.title("Changed List")
                .addItem(display("Late Item"))
                .utility(UtilitySlot.RIGHT_1, display("Changed Utility"));

        MenuFrame secondPage = menu.frame("page:1");
        assertEquals("Original List (2/2)", ComponentText.flatten(secondPage.title()));
        assertEquals("Item 28", titleAt(secondPage, 10));
        assertEquals("minecraft:air", secondPage.slots().get(11).icon().key());
        assertEquals("Original Utility", titleAt(secondPage, 50));
    }

    @Test
    void tabsBuildSnapshotsLazyFrameInputsAndTabContent() {
        List<MenuItem> alphaItems = new ArrayList<>(items("Alpha", 22));
        TabsMenuBuilder builder = menus.tabs()
                .title("Original Tabs")
                .defaultTab("alpha")
                .addTab(MenuTab.of("alpha", "Alpha", MenuIcon.vanilla("stone"), alphaItems))
                .addTab(MenuTab.of("beta", "Beta", MenuIcon.vanilla("diamond"), items("Beta", 1)))
                .utility(UtilitySlot.RIGHT_1, display("Original Utility"));

        Menu menu = builder.build();

        alphaItems.add(display("Late Content"));
        builder.title("Changed Tabs")
                .utility(UtilitySlot.RIGHT_1, display("Changed Utility"))
                .customFooter()
                .addTab(MenuTab.of("gamma", "Gamma", MenuIcon.vanilla("emerald"), items("Gamma", 1)));

        MenuFrame secondPage = menu.frame("tab:alpha:nav:0:page:1");
        assertEquals("Original Tabs", ComponentText.flatten(secondPage.title()));
        assertEquals("Alpha 21", titleAt(secondPage, 19));
        assertEquals("minecraft:air", secondPage.slots().get(20).icon().key());
        assertEquals("Original Utility", titleAt(secondPage, 50));
        assertEquals("Close", titleAt(secondPage, 49));
        assertFalse(menu.frameIds().stream().anyMatch(frameId -> frameId.startsWith("tab:gamma:")));
    }

    @Test
    void canvasBuildSnapshotsAllBuilderFields() {
        CanvasMenuBuilder builder = menus.canvas()
                .title("Original Canvas")
                .rows(6)
                .place(20, display("Original Placement"))
                .utility(UtilitySlot.RIGHT_1, display("Original Utility"));

        Menu menu = builder.build();

        builder.title("Changed Canvas")
                .rows(3)
                .place(21, display("Late Placement"))
                .utility(UtilitySlot.RIGHT_1, display("Changed Utility"));

        MenuFrame frame = menu.initialFrame();
        assertEquals("Original Canvas", ComponentText.flatten(frame.title()));
        assertEquals(6, menu.rows());
        assertEquals("Original Placement", titleAt(frame, 20));
        assertEquals("minecraft:black_stained_glass_pane", frame.slots().get(21).icon().key());
        assertEquals("Original Utility", titleAt(frame, 50));
    }

    @Test
    void confirmationBuildSnapshotsAllBuilderFields() {
        MenuDisplayItem originalInfo = display("Original Info");
        MenuButton originalCancel = button("Original Cancel", ActionVerb.BACK);
        MenuButton originalConfirm = button("Original Confirm", ActionVerb.CONFIRM);
        ConfirmationMenuBuilder builder = menus.confirmation()
                .title("Original Confirmation")
                .info(originalInfo)
                .cancel(originalCancel)
                .confirm(originalConfirm);

        Menu menu = builder.build();

        builder.title("Changed Confirmation")
                .info(display("Changed Info"))
                .cancel(button("Changed Cancel", ActionVerb.BACK))
                .confirm(button("Changed Confirm", ActionVerb.CONFIRM));

        MenuFrame frame = menu.initialFrame();
        assertEquals("Original Confirmation", ComponentText.flatten(frame.title()));
        assertEquals("Original Info", titleAt(frame, 13));
        assertEquals("Original Cancel", titleAt(frame, 29));
        assertEquals("Original Confirm", titleAt(frame, 33));
    }

    private static List<MenuItem> items(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> (MenuItem) display(prefix + " " + index))
                .toList();
    }

    private static MenuDisplayItem display(String name) {
        return MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                .name(name)
                .build();
    }

    private static MenuButton button(String name, ActionVerb verb) {
        return MenuButton.builder(MenuIcon.vanilla("stone"))
                .name(name)
                .action(verb, context -> { })
                .build();
    }

    private static String titleAt(MenuFrame frame, int slot) {
        return ComponentText.flatten(frame.slots().get(slot).title());
    }
}
