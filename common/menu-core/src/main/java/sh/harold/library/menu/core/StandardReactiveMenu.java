package sh.harold.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuGeometry;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuInteraction;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.MenuSlotAction;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveMenuCustodyPolicy;
import sh.harold.library.menu.ReactiveMenuReducer;
import sh.harold.library.menu.ReactiveMenuRenderer;
import sh.harold.library.menu.ReactiveMenuResult;
import sh.harold.library.menu.ReactiveMenuView;
import sh.harold.library.menu.UtilitySlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final Map<String, Integer> custodyTargets;
    private final ReactiveMenuCustodyPolicy<? super S> custodyPolicy;
    private final List<MenuSlot> filledBaseSlots;
    private final List<MenuSlot> emptyBaseSlots;

    StandardReactiveMenu(
            int rows,
            Map<UtilitySlot, MenuItem> utilities,
            boolean fillWithBlackPane,
            long tickIntervalTicks,
            Supplier<? extends S> stateFactory,
            ReactiveMenuRenderer<? super S> renderer,
            ReactiveMenuReducer<? super S> reducer,
            Map<String, Integer> custodyTargets,
            ReactiveMenuCustodyPolicy<? super S> custodyPolicy
    ) {
        this.rows = rows;
        this.utilities = Map.copyOf(new LinkedHashMap<>(utilities));
        this.fillWithBlackPane = fillWithBlackPane;
        this.tickIntervalTicks = tickIntervalTicks;
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        this.custodyTargets = Collections.unmodifiableMap(new LinkedHashMap<>(custodyTargets));
        this.custodyPolicy = custodyPolicy;
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

    @SuppressWarnings("unchecked")
    public ReactiveMenuResult<?> reduce(Object state, ReactiveMenuInput input) {
        return Objects.requireNonNull(((ReactiveMenuReducer<S>) reducer).reduce((S) state, input), "reducer.reduce(...)");
    }

    @Override
    public long tickIntervalTicks() {
        return tickIntervalTicks;
    }

    @Override
    public Map<String, Integer> custodyTargets() {
        return custodyTargets;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MenuCustodyDecision decideCustody(Object state, MenuCustodyGesture gesture, MenuCustodySnapshot snapshot) {
        if (custodyPolicy == null) {
            return MenuCustodyDecision.reject();
        }
        return Objects.requireNonNull(
                ((ReactiveMenuCustodyPolicy<S>) custodyPolicy).decide((S) state, gesture, snapshot),
                "custodyPolicy.decide(...)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public MenuSessionView buildView(Object state, ReactivePlacementCache cache) {
        ReactiveMenuView rendered = MenuTrace.time("state.reactive.render",
                () -> Objects.requireNonNull(renderer.render((S) state), "renderer.render(...)"));
        MenuTrace.title(rendered.title());
        MenuTrace.setCount("placementCount", rendered.placements().size());
        boolean renderFilledBase = rendered.fillWithBlackPane() != null ? rendered.fillWithBlackPane() : fillWithBlackPane;
        List<MenuSlot> baseSlots = MenuTrace.time("state.reactive.baseSlots",
                () -> renderFilledBase ? filledBaseSlots : emptyBaseSlots);
        int size = baseSlots.size();
        if (rendered.placements().isEmpty()) {
            return new MenuSessionView(rendered.title(), baseSlots);
        }
        List<MenuSlot> slots = new ArrayList<>(baseSlots);
        Set<Integer> reactiveClickTargets = new HashSet<>();
        for (Map.Entry<Integer, MenuItem> entry : rendered.placements().entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("Reactive view slot " + slot + " is outside a " + rows + "-row menu");
            }
            slots.set(slot, cache.compile(slot, entry.getValue()));
            reactiveClickTargets.add(slot);
        }
        return new MenuSessionView(rendered.title(), List.copyOf(slots), reactiveClickTargets);
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
