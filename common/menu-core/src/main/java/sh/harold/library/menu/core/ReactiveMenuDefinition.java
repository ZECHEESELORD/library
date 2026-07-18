package sh.harold.library.menu.core;

import sh.harold.library.menu.ReactiveMenu;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveMenuResult;

interface ReactiveMenuDefinition extends ReactiveMenu {

    Object createState();

    ReactiveMenuResult<?> reduce(Object state, ReactiveMenuInput input);

    long tickIntervalTicks();

    MenuSessionView buildView(Object state, ReactivePlacementCache cache);

    default java.util.Map<String, Integer> custodyTargets() {
        return java.util.Map.of();
    }

    default MenuCustodyDecision decideCustody(Object state, MenuCustodyGesture gesture, MenuCustodySnapshot snapshot) {
        return MenuCustodyDecision.reject();
    }
}
