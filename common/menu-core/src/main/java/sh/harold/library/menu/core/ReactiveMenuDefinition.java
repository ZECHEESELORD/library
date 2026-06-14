package sh.harold.library.menu.core;

import sh.harold.library.menu.ReactiveMenu;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveMenuResult;

interface ReactiveMenuDefinition extends ReactiveMenu {

    Object createState();

    ReactiveMenuResult<?> reduce(Object state, ReactiveMenuInput input);

    long tickIntervalTicks();

    MenuSessionView buildView(Object state, ReactivePlacementCache cache);
}
