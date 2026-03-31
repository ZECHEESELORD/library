package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.List;
import java.util.Map;

interface ReactiveMenuDefinition extends ReactiveMenu {

    Object createState();

    ReactiveMenuView render(Object state);

    ReactiveMenuResult<?> reduce(Object state, ReactiveMenuInput input);

    Map<UtilitySlot, MenuItem> utilities();

    boolean fillWithBlackPane();

    List<MenuSlot> baseSlots(boolean fillWithBlackPane);

    long tickIntervalTicks();
}
