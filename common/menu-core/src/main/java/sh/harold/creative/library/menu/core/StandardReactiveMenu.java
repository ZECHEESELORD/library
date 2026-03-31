package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuReducer;
import sh.harold.creative.library.menu.ReactiveMenuRenderer;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class StandardReactiveMenu<S> implements ReactiveMenuDefinition {

    private static final int FOOTER_CLOSE_OFFSET = 4;

    private final int rows;
    private final Map<UtilitySlot, MenuItem> utilities;
    private final boolean fillWithBlackPane;
    private final long tickIntervalTicks;
    private final Supplier<? extends S> stateFactory;
    private final ReactiveMenuRenderer<? super S> renderer;
    private final ReactiveMenuReducer<? super S> reducer;
    private final List<MenuSlot> filledBaseSlots;
    private final List<MenuSlot> emptyBaseSlots;

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
        this.filledBaseSlots = buildBaseSlots(rows, this.utilities, true);
        this.emptyBaseSlots = buildBaseSlots(rows, this.utilities, false);
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
    public List<MenuSlot> baseSlots(boolean fillWithBlackPane) {
        return fillWithBlackPane ? filledBaseSlots : emptyBaseSlots;
    }

    @Override
    public long tickIntervalTicks() {
        return tickIntervalTicks;
    }

    private static List<MenuSlot> buildBaseSlots(int rows, Map<UtilitySlot, MenuItem> utilities, boolean fillWithBlackPane) {
        int size = rows * 9;
        List<MenuSlot> slots = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            slots.add(fillWithBlackPane ? filler(slot) : empty(slot));
        }
        int footerStart = HouseMenuCompiler.footerStart(rows);
        for (Map.Entry<UtilitySlot, MenuItem> entry : utilities.entrySet()) {
            int slot = entry.getKey().resolveSlot(footerStart);
            slots.set(slot, HouseMenuCompiler.compile(slot, entry.getValue()));
        }
        int closeSlot = footerStart + FOOTER_CLOSE_OFFSET;
        if (closeSlot < size) {
            slots.set(closeSlot, closeButton(closeSlot));
        }
        return List.copyOf(slots);
    }

    private static MenuSlot filler(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("black_stained_glass_pane"), Component.text(" "), List.of(), false, Map.of());
    }

    private static MenuSlot empty(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("air"), Component.empty(), List.of(), false, Map.of());
    }

    private static MenuSlot closeButton(int slot) {
        return new MenuSlot(slot,
                MenuIcon.vanilla("barrier"),
                Component.text("Close", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(),
                false,
                Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.CLOSE, new MenuSlotAction.Close())));
    }
}
