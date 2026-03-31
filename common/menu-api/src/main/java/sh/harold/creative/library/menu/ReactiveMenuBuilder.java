package sh.harold.creative.library.menu;

import java.util.function.Supplier;

public interface ReactiveMenuBuilder<S> {

    <T> ReactiveMenuBuilder<T> state(T state);

    <T> ReactiveMenuBuilder<T> stateFactory(Supplier<? extends T> stateFactory);

    ReactiveMenuBuilder<S> rows(int rows);

    ReactiveMenuBuilder<S> utility(UtilitySlot slot, MenuItem item);

    ReactiveMenuBuilder<S> fillWithBlackPane(boolean fillWithBlackPane);

    ReactiveMenuBuilder<S> tickEvery(long tickIntervalTicks);

    ReactiveMenuBuilder<S> render(ReactiveMenuRenderer<? super S> renderer);

    ReactiveMenuBuilder<S> reduce(ReactiveMenuReducer<? super S> reducer);

    ReactiveMenu build();
}
