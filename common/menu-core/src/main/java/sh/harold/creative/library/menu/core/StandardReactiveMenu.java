package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuReducer;
import sh.harold.creative.library.menu.ReactiveMenuRenderer;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class StandardReactiveMenu<S> implements ReactiveMenuDefinition {

    private final int rows;
    private final Map<UtilitySlot, MenuItem> utilities;
    private final boolean fillWithBlackPane;
    private final long tickIntervalTicks;
    private final Supplier<? extends S> stateFactory;
    private final ReactiveMenuRenderer<? super S> renderer;
    private final ReactiveMenuReducer<? super S> reducer;

    StandardReactiveMenu(
            int rows,
            Map<UtilitySlot, MenuItem> utilities,
            boolean fillWithBlackPane,
            long tickIntervalTicks,
            Supplier<? extends S> stateFactory,
            ReactiveMenuRenderer<? super S> renderer,
            ReactiveMenuReducer<? super S> reducer
    ) {
        this.rows = rows;
        this.utilities = Map.copyOf(new LinkedHashMap<>(utilities));
        this.fillWithBlackPane = fillWithBlackPane;
        this.tickIntervalTicks = tickIntervalTicks;
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
    }

    @Override
    public MenuGeometry geometry() {
        return MenuGeometry.CANVAS;
    }

    @Override
    public int rows() {
        return rows;
    }

    @Override
    public Object createState() {
        return stateFactory.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReactiveMenuView render(Object state) {
        return Objects.requireNonNull(renderer.render((S) state), "renderer.render(...)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReactiveMenuResult<?> reduce(Object state, ReactiveMenuInput input) {
        return Objects.requireNonNull(((ReactiveMenuReducer<S>) reducer).reduce((S) state, input), "reducer.reduce(...)");
    }

    @Override
    public Map<UtilitySlot, MenuItem> utilities() {
        return utilities;
    }

    @Override
    public boolean fillWithBlackPane() {
        return fillWithBlackPane;
    }

    @Override
    public long tickIntervalTicks() {
        return tickIntervalTicks;
    }
}
