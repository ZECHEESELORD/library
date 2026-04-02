package sh.harold.creative.library.menu;

import java.util.function.Supplier;

public interface ReactiveListMenuBuilder<S> {

    <T> ReactiveListMenuBuilder<T> state(T state);

    <T> ReactiveListMenuBuilder<T> stateFactory(Supplier<? extends T> stateFactory);

    ReactiveListMenuBuilder<S> utility(UtilitySlot slot, MenuItem item);

    ReactiveListMenuBuilder<S> tickEvery(long tickIntervalTicks);

    ReactiveListMenuBuilder<S> render(ReactiveListRenderer<? super S> renderer);

    ReactiveListMenuBuilder<S> reduce(ReactiveMenuReducer<? super S> reducer);

    ReactiveMenu build();
}
