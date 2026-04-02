package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.ReactiveGeometryAction;
import sh.harold.creative.library.menu.ReactiveListRenderer;
import sh.harold.creative.library.menu.ReactiveListView;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuReducer;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;

final class StandardReactiveListMenu<S> implements ReactiveMenuDefinition {

    private final Map<UtilitySlot, MenuItem> utilities;
    private final long tickIntervalTicks;
    private final Supplier<? extends S> stateFactory;
    private final ReactiveListRenderer<? super S> renderer;
    private final ReactiveMenuReducer<? super S> reducer;
    private final List<MenuSlot> baseSlots;

    StandardReactiveListMenu(
            Map<UtilitySlot, MenuItem> utilities,
            long tickIntervalTicks,
            Supplier<? extends S> stateFactory,
            ReactiveListRenderer<? super S> renderer,
            ReactiveMenuReducer<? super S> reducer
    ) {
        this.utilities = Map.copyOf(new LinkedHashMap<>(utilities));
        this.tickIntervalTicks = tickIntervalTicks;
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        this.baseSlots = buildBaseSlots(this.utilities);
    }

    @Override
    public MenuGeometry geometry() {
        return MenuGeometry.LIST;
    }

    @Override
    public int rows() {
        return StandardMenuService.LIST_ROWS;
    }

    @Override
    public Object createState() {
        return stateFactory.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReactiveMenuResult<?> reduce(Object state, ReactiveMenuInput input) {
        return Objects.requireNonNull(((ReactiveMenuReducer<S>) reducer).reduce((S) state, input), "reducer.reduce(...)");
    }

    @Override
    public long tickIntervalTicks() {
        return tickIntervalTicks;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MenuSessionView buildView(Object state, ReactivePlacementCache cache) {
        ReactiveListView rendered = MenuTrace.time("state.reactive.render",
                () -> Objects.requireNonNull(renderer.render((S) state), "renderer.render(...)"));
        int pageIndex = PagedListSupport.clampPageIndex(rendered.pageIndex(), rendered.items().size(), PagedListSupport.PURE_LIST_PAGE_SIZE);
        int totalPages = PagedListSupport.pageCount(rendered.items().size(), PagedListSupport.PURE_LIST_PAGE_SIZE);
        Component title = PagedListSupport.decorateTitle(rendered.title(), pageIndex, totalPages);
        MenuTrace.title(title);

        List<MenuSlot> slots = new ArrayList<>(baseSlots);
        Set<Integer> touchedSlots = new HashSet<>();
        Set<Integer> reactiveClickTargets = new HashSet<>();

        int firstItem = PagedListSupport.firstItemIndex(pageIndex, PagedListSupport.PURE_LIST_PAGE_SIZE);
        int lastItem = PagedListSupport.lastItemExclusive(pageIndex, rendered.items().size(), PagedListSupport.PURE_LIST_PAGE_SIZE);
        int slotIndex = 0;
        for (int itemIndex = firstItem; itemIndex < lastItem; itemIndex++) {
            int slot = PagedListSupport.PURE_LIST_CONTENT_SLOTS.get(slotIndex++);
            slots.set(slot, cache.compile(slot, rendered.items().get(itemIndex)));
            touchedSlots.add(slot);
            reactiveClickTargets.add(slot);
        }

        int footerStart = HouseMenuCompiler.footerStart(StandardMenuService.LIST_ROWS);
        if (totalPages > 1 && pageIndex > 0) {
            int slot = footerStart + StandardMenuService.FOOTER_PREVIOUS_OFFSET;
            slots.set(slot, StandardMenuService.navigationButton(slot, "Previous Page", pageIndex,
                    sh.harold.creative.library.menu.MenuIcon.vanilla("arrow"),
                    Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.PREVIOUS_PAGE,
                            new MenuSlotAction.Dispatch(new ReactiveGeometryAction.PreviousPage())))));
            touchedSlots.add(slot);
        }
        if (totalPages > 1 && pageIndex + 1 < totalPages) {
            int slot = footerStart + StandardMenuService.FOOTER_NEXT_OFFSET;
            slots.set(slot, StandardMenuService.navigationButton(slot, "Next Page", pageIndex + 2,
                    sh.harold.creative.library.menu.MenuIcon.vanilla("arrow"),
                    Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.NEXT_PAGE,
                            new MenuSlotAction.Dispatch(new ReactiveGeometryAction.NextPage())))));
            touchedSlots.add(slot);
        }

        MenuTrace.setCount("placementCount", touchedSlots.size());
        return new MenuSessionView(title, List.copyOf(slots), null, reactiveClickTargets);
    }

    private static List<MenuSlot> buildBaseSlots(Map<UtilitySlot, MenuItem> utilities) {
        Map<Integer, MenuSlot> slots = StandardMenuService.createFilledSlots(StandardMenuService.LIST_ROWS);
        for (int slot : PagedListSupport.PURE_LIST_CONTENT_SLOTS) {
            slots.put(slot, StandardMenuService.empty(slot));
        }
        int footerStart = HouseMenuCompiler.footerStart(StandardMenuService.LIST_ROWS);
        StandardMenuService.applyUtilities(slots, footerStart, utilities);
        StandardMenuService.applySharedFooter(slots, footerStart, null, null);
        return StandardMenuService.orderedSlots(slots, StandardMenuService.LIST_ROWS);
    }
}
