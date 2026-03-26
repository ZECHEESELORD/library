package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

public interface TabsMenuBuilder {

    /**
     * House footer policy for v1 tab menus:
     * slot 45 = previous/back,
     * slot 49 = close,
     * slot 53 = next,
     * remaining bottom-row slots = utility controls or black-pane filler.
     * The top row is reserved for the tab strip.
     */

    TabsMenuBuilder title(String title);

    TabsMenuBuilder title(Component title);

    TabsMenuBuilder back(MenuAction action);

    TabsMenuBuilder utility(UtilitySlot slot, MenuItem item);

    TabsMenuBuilder defaultTab(String tabId);

    TabsMenuBuilder addTab(MenuTab tab);

    Menu build();
}
