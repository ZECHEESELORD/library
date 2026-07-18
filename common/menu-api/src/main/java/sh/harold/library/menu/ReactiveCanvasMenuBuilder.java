package sh.harold.library.menu;

import java.util.function.Supplier;

public interface ReactiveCanvasMenuBuilder<S> extends ReactiveMenuBuilder<S> {

    @Override
    <T> ReactiveCanvasMenuBuilder<T> state(T state);

    @Override
    <T> ReactiveCanvasMenuBuilder<T> stateFactory(Supplier<? extends T> stateFactory);

    @Override
    ReactiveCanvasMenuBuilder<S> rows(int rows);

    @Override
    ReactiveCanvasMenuBuilder<S> utility(UtilitySlot slot, MenuItem item);

    @Override
    ReactiveCanvasMenuBuilder<S> fillWithBlackPane(boolean fillWithBlackPane);

    @Override
    ReactiveCanvasMenuBuilder<S> tickEvery(long tickIntervalTicks);

    @Override
    ReactiveCanvasMenuBuilder<S> render(ReactiveMenuRenderer<? super S> renderer);

    @Override
    ReactiveCanvasMenuBuilder<S> reduce(ReactiveMenuReducer<? super S> reducer);

    /**
     * Declares a fixed runtime-owned location for one exact native stack.
     */
    ReactiveCanvasMenuBuilder<S> custodyTarget(String key, int slot);

    /**
     * Selects allowed destinations without directly mutating native inventory state.
     */
    ReactiveCanvasMenuBuilder<S> custodyPolicy(ReactiveMenuCustodyPolicy<? super S> policy);
}
