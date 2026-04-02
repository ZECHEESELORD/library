package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;

interface ReactiveMenuDefinition extends ReactiveMenu {

    Object createState();

    ReactiveMenuResult<?> reduce(Object state, ReactiveMenuInput input);

    long tickIntervalTicks();

    MenuSessionView buildView(Object state, ReactivePlacementCache cache);
}
