package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

public interface TabsMenuBuilder {

    /**
     * House footer policy for v1 tab menus:
     * row 0 = tab strip,
     * row 1 = black-pane spacer,
     * rows 2-4 = tab content,
     * slot 45 = previous/back,
     * slot 49 = close,
     * slot 53 = next,
     * remaining bottom-row slots = utility controls or black-pane filler.
     */

    TabsMenuBuilder title(String title);

    TabsMenuBuilder title(Component title);

    TabsMenuBuilder back(MenuAction action);

    TabsMenuBuilder utility(UtilitySlot slot, MenuItem item);

    TabsMenuBuilder defaultTab(String tabId);

    TabsMenuBuilder addTab(MenuTab tab);

    Menu build();
}
