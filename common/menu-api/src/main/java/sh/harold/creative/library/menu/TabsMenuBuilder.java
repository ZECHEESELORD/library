package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

public interface TabsMenuBuilder {

    /**
     * House footer policy for v1 tab menus:
     * row 0 = tab strip,
     * row 1 = tab-state chrome under the visible strip,
     * shared footer mode reserves row 5 for back/close/paging utilities by default,
     * custom footer mode makes rows 2-5 caller-owned,
     * tabs may be grouped explicitly and may use list or canvas content specs,
     * list tab panels leave their bordered 3x7 interior open by default,
     * canvas tab panels keep content-area black filler on by default and may explicitly opt out,
     * and canvas tab layouts should bias sparse authored content toward centered row-3 placements first.
     */

    TabsMenuBuilder title(String title);

    TabsMenuBuilder title(Component title);

    TabsMenuBuilder back(MenuAction action);

    TabsMenuBuilder utility(UtilitySlot slot, MenuItem item);

    TabsMenuBuilder customFooter();

    TabsMenuBuilder defaultTab(String tabId);

    TabsMenuBuilder addGroup(MenuTabGroup group);

    TabsMenuBuilder addTab(MenuTab tab);

    Menu build();
}
