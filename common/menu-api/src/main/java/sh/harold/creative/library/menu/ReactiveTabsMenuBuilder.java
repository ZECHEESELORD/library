package sh.harold.creative.library.menu;

import java.util.function.Supplier;

public interface ReactiveTabsMenuBuilder<S> {

    <T> ReactiveTabsMenuBuilder<T> state(T state);

    <T> ReactiveTabsMenuBuilder<T> stateFactory(Supplier<? extends T> stateFactory);

    ReactiveTabsMenuBuilder<S> utility(UtilitySlot slot, MenuItem item);

    ReactiveTabsMenuBuilder<S> customFooter();

    ReactiveTabsMenuBuilder<S> tickEvery(long tickIntervalTicks);

    ReactiveTabsMenuBuilder<S> render(ReactiveTabsRenderer<? super S> renderer);

    ReactiveTabsMenuBuilder<S> reduce(ReactiveMenuReducer<? super S> reducer);

    ReactiveMenu build();
}
