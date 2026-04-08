package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.ReactiveGeometryAction;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuReducer;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveTabsRenderer;
import sh.harold.creative.library.menu.ReactiveTabsView;
import sh.harold.creative.library.menu.UtilitySlot;
import sh.harold.creative.library.sound.SoundCueKeys;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

final class StandardReactiveTabsMenu<S> implements ReactiveMenuDefinition {

    private final Map<UtilitySlot, MenuItem> utilities;
    private final boolean sharedFooter;
    private final long tickIntervalTicks;
    private final Supplier<? extends S> stateFactory;
    private final ReactiveTabsRenderer<? super S> renderer;
    private final ReactiveMenuReducer<? super S> reducer;
    private final List<MenuSlot> sharedFooterBaseSlots;
    private final List<MenuSlot> customFooterBaseSlots;

    StandardReactiveTabsMenu(
            Map<UtilitySlot, MenuItem> utilities,
            boolean sharedFooter,
            long tickIntervalTicks,
            Supplier<? extends S> stateFactory,
            ReactiveTabsRenderer<? super S> renderer,
            ReactiveMenuReducer<? super S> reducer
    ) {
        this.utilities = Map.copyOf(new LinkedHashMap<>(utilities));
        this.sharedFooter = sharedFooter;
        this.tickIntervalTicks = tickIntervalTicks;
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        this.sharedFooterBaseSlots = buildBaseSlots(true);
        this.customFooterBaseSlots = buildBaseSlots(Map.of(), false);
    }

    @Override
    public MenuGeometry geometry() {
        return MenuGeometry.TABS;
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
        ReactiveTabsView rendered = MenuTrace.time("state.reactive.render",
                () -> Objects.requireNonNull(renderer.render((S) state), "renderer.render(...)"));
        MenuTrace.title(rendered.title());

        List<StandardMenuService.FlatTab> flatTabs = StandardMenuService.flattenTabs(rendered.groups());
        validateTabs(flatTabs);
        validateFooterMode(flatTabs);
        validateTabPlacements(flatTabs);

        StandardMenuService.NavPlan navPlan = StandardMenuService.buildNavPlan(flatTabs);
        String activeTabId = resolveActiveTabId(flatTabs, rendered.activeTabId());
        MenuTab activeTab = flatTabs.stream()
                .map(StandardMenuService.FlatTab::tab)
                .filter(tab -> tab.id().equals(activeTabId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Active tab id disappeared after resolution: " + activeTabId));
        int navStart = clampNavStart(rendered.navStart(), navPlan);
        int pageIndex = activeTab.content() instanceof MenuTabContent.ListContent list
                ? PagedListSupport.clampPageIndex(rendered.pageIndex(), list.items().size(), PagedListSupport.TAB_LIST_PAGE_SIZE)
                : 0;

        List<MenuSlot> slots = new ArrayList<>(sharedFooter ? sharedFooterBaseSlots : customFooterBaseSlots);
        Set<Integer> touchedSlots = new HashSet<>();
        Set<Integer> reactiveClickTargets = new HashSet<>();
        int listPageCount = activeTab.content() instanceof MenuTabContent.ListContent list
                ? PagedListSupport.pageCount(list.items().size(), PagedListSupport.TAB_LIST_PAGE_SIZE)
                : 1;

        renderTabChrome(slots, flatTabs, navPlan, activeTab, navStart, touchedSlots);
        if (activeTab.content() instanceof MenuTabContent.ListContent list) {
            renderListContent(slots, list.items(), pageIndex, touchedSlots, reactiveClickTargets, cache);
            if (sharedFooter) {
                renderPagedFooter(slots, list.items().size(), pageIndex, touchedSlots);
            }
        } else if (activeTab.content() instanceof MenuTabContent.CanvasContent canvas) {
            renderCanvasContent(slots, canvas, touchedSlots, reactiveClickTargets, cache);
        }

        if (sharedFooter) {
            int footerStart = HouseMenuCompiler.footerStart(StandardMenuService.LIST_ROWS);
            Map<UtilitySlot, MenuItem> utilities = mergedUtilities(rendered.utilities());
            StandardMenuService.validateUtilitySlots(utilities, footerStart,
                    StandardMenuService.reservedSharedFooterSlots(footerStart,
                            listPageCount > 1 && pageIndex > 0,
                            listPageCount > 1 && pageIndex + 1 < listPageCount));
            for (Map.Entry<UtilitySlot, MenuItem> utility : utilities.entrySet()) {
                int slot = utility.getKey().resolveSlot(footerStart);
                slots.set(slot, cache.compile(slot, utility.getValue()));
                touchedSlots.add(slot);
            }
        }

        MenuTrace.setCount("placementCount", touchedSlots.size());
        return new MenuSessionView(rendered.title(), List.copyOf(slots), null, reactiveClickTargets);
    }

    private void renderTabChrome(
            List<MenuSlot> slots,
            List<StandardMenuService.FlatTab> flatTabs,
            StandardMenuService.NavPlan navPlan,
            MenuTab activeTab,
            int navStart,
            Set<Integer> touchedSlots
    ) {
        StandardMenuService.NavWindow window = navPlan.window(navStart);
        List<StandardMenuService.PositionedTab> positioned = StandardMenuService.positionTabs(flatTabs, window, navPlan.overflow());
        for (StandardMenuService.PositionedTab position : positioned) {
            MenuTab tab = position.flatTab().tab();
            boolean active = tab.id().equals(activeTab.id());
            slots.set(position.slot(), reactiveTabButton(position.slot(), tab, active));
            touchedSlots.add(position.slot());
            int indicatorSlot = position.slot() + StandardMenuService.TABS_INDICATOR_ROW_START;
            slots.set(indicatorSlot, StandardMenuService.tabIndicator(indicatorSlot, active));
            touchedSlots.add(indicatorSlot);
        }
        if (navPlan.overflow()) {
            int leftSlot = StandardMenuService.TABS_NAV_LEFT_SLOT;
            slots.set(leftSlot, reactiveTabsArrow(leftSlot, "Previous Tab",
                    Math.max(0, navStart - 1) + 1,
                    navStart > 0 ? new ReactiveGeometryAction.PreviousTabs() : null,
                    navStart > 0 ? new ReactiveGeometryAction.JumpToFirstTabs() : null));
            touchedSlots.add(leftSlot);

            int rightSlot = StandardMenuService.TABS_NAV_RIGHT_SLOT;
            slots.set(rightSlot, reactiveTabsArrow(rightSlot, "Next Tab",
                    Math.min(navPlan.lastStart(), navStart + 1) + 1,
                    navStart < navPlan.lastStart() ? new ReactiveGeometryAction.NextTabs() : null,
                    navStart < navPlan.lastStart() ? new ReactiveGeometryAction.JumpToLastTabs() : null));
            touchedSlots.add(rightSlot);
        }
    }

    private void renderListContent(
            List<MenuSlot> slots,
            List<MenuItem> items,
            int pageIndex,
            Set<Integer> touchedSlots,
            Set<Integer> reactiveClickTargets,
            ReactivePlacementCache cache
    ) {
        for (int slot : PagedListSupport.TAB_LIST_CONTENT_SLOTS) {
            slots.set(slot, StandardMenuService.empty(slot));
            touchedSlots.add(slot);
        }
        int firstItem = PagedListSupport.firstItemIndex(pageIndex, PagedListSupport.TAB_LIST_PAGE_SIZE);
        int lastItem = PagedListSupport.lastItemExclusive(pageIndex, items.size(), PagedListSupport.TAB_LIST_PAGE_SIZE);
        int slotIndex = 0;
        for (int itemIndex = firstItem; itemIndex < lastItem; itemIndex++) {
            int slot = PagedListSupport.TAB_LIST_CONTENT_SLOTS.get(slotIndex++);
            slots.set(slot, cache.compile(slot, items.get(itemIndex)));
            touchedSlots.add(slot);
            reactiveClickTargets.add(slot);
        }
    }

    private void renderPagedFooter(List<MenuSlot> slots, int itemCount, int pageIndex, Set<Integer> touchedSlots) {
        int totalPages = PagedListSupport.pageCount(itemCount, PagedListSupport.TAB_LIST_PAGE_SIZE);
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
    }

    private void renderCanvasContent(
            List<MenuSlot> slots,
            MenuTabContent.CanvasContent canvas,
            Set<Integer> touchedSlots,
            Set<Integer> reactiveClickTargets,
            ReactivePlacementCache cache
    ) {
        if (!canvas.fillWithBlackPane()) {
            int end = sharedFooter ? StandardMenuService.TABS_SHARED_CONTENT_END : StandardMenuService.TABS_CUSTOM_CONTENT_END;
            for (int slot = StandardMenuService.TABS_CONTENT_START; slot <= end; slot++) {
                slots.set(slot, StandardMenuService.empty(slot));
                touchedSlots.add(slot);
            }
        }
        for (Map.Entry<Integer, MenuItem> entry : canvas.placements().entrySet()) {
            slots.set(entry.getKey(), cache.compile(entry.getKey(), entry.getValue()));
            touchedSlots.add(entry.getKey());
            reactiveClickTargets.add(entry.getKey());
        }
    }

    private static MenuSlot reactiveTabButton(int slot, MenuTab tab, boolean active) {
        Map<MenuClick, MenuInteraction> interactions = Map.of(
                MenuClick.LEFT, MenuInteraction.of(ActionVerb.SWITCH_TAB,
                        new MenuSlotAction.Dispatch(new ReactiveGeometryAction.SwitchTab(tab.id()))));
        if (tab.secondary() == null && tab.blocks().isEmpty()) {
            return StandardMenuService.chromeButton(slot, tab.name(), tab.icon(), interactions, active || tab.glow());
        }
        return HouseMenuCompiler.compile(slot, tab.icon(), tab.name(), tab.secondary(), tab.blocks(),
                active || tab.glow(), interactions, false, 1);
    }

    private static MenuSlot reactiveTabsArrow(int slot, String title, int pageNumber, Object leftMessage, Object rightMessage) {
        if (leftMessage == null && rightMessage == null) {
            return StandardMenuService.navigationButton(slot, title, pageNumber,
                    sh.harold.creative.library.menu.MenuIcon.vanilla("arrow"), Map.of());
        }
        Map<MenuClick, MenuInteraction> interactions = new EnumMap<>(MenuClick.class);
        if (leftMessage != null) {
            ActionVerb verb = title.startsWith("Previous") ? ActionVerb.PREVIOUS_PAGE : ActionVerb.NEXT_PAGE;
            interactions.put(MenuClick.LEFT, MenuInteraction.of(verb,
                    title.startsWith("Previous") ? "browse previous tabs" : "browse next tabs",
                    new MenuSlotAction.Dispatch(leftMessage)));
        }
        if (rightMessage != null) {
            interactions.put(MenuClick.RIGHT, MenuInteraction.of(ActionVerb.BROWSE,
                    title.startsWith("Previous") ? "jump to first tabs" : "jump to last tabs",
                    new MenuSlotAction.Dispatch(rightMessage),
                    SoundCueKeys.MENU_SCROLL));
        }
        return StandardMenuService.navigationButton(slot, title, pageNumber,
                sh.harold.creative.library.menu.MenuIcon.vanilla("arrow"), interactions);
    }

    private static String resolveActiveTabId(List<StandardMenuService.FlatTab> flatTabs, String activeTabId) {
        for (StandardMenuService.FlatTab flatTab : flatTabs) {
            if (flatTab.tab().id().equals(activeTabId)) {
                return activeTabId;
            }
        }
        return flatTabs.get(0).tab().id();
    }

    private static int clampNavStart(int navStart, StandardMenuService.NavPlan navPlan) {
        if (!navPlan.overflow()) {
            return 0;
        }
        return Math.max(0, Math.min(navStart, navPlan.lastStart()));
    }

    private static void validateTabs(List<StandardMenuService.FlatTab> flatTabs) {
        if (flatTabs.size() < 2) {
            throw new IllegalStateException("Tabs menu requires at least two tabs");
        }
        Set<String> ids = new HashSet<>();
        for (StandardMenuService.FlatTab flatTab : flatTabs) {
            if (!ids.add(flatTab.tab().id())) {
                throw new IllegalStateException("Duplicate tab id: " + flatTab.tab().id());
            }
        }
    }

    private void validateFooterMode(List<StandardMenuService.FlatTab> flatTabs) {
        if (sharedFooter) {
            return;
        }
        for (StandardMenuService.FlatTab flatTab : flatTabs) {
            if (flatTab.tab().content() instanceof MenuTabContent.ListContent) {
                throw new IllegalStateException("List tab content requires the shared footer");
            }
        }
    }

    private void validateTabPlacements(List<StandardMenuService.FlatTab> flatTabs) {
        for (StandardMenuService.FlatTab flatTab : flatTabs) {
            if (!(flatTab.tab().content() instanceof MenuTabContent.CanvasContent canvas)) {
                continue;
            }
            for (Integer slot : canvas.placements().keySet()) {
                if (slot < StandardMenuService.TABS_CONTENT_START) {
                    throw new IllegalArgumentException("Tab canvas slot " + slot + " collides with reserved tab chrome");
                }
                if (sharedFooter && slot > StandardMenuService.TABS_SHARED_CONTENT_END) {
                    throw new IllegalArgumentException("Shared-footer tabs may not place content in row 5");
                }
                if (!sharedFooter && slot > StandardMenuService.TABS_CUSTOM_CONTENT_END) {
                    throw new IllegalArgumentException("Tab canvas slot " + slot + " is outside the tab menu");
                }
            }
        }
    }

    private Map<UtilitySlot, MenuItem> mergedUtilities(Map<UtilitySlot, MenuItem> renderedUtilities) {
        if (renderedUtilities.isEmpty()) {
            return utilities;
        }
        Map<UtilitySlot, MenuItem> merged = new LinkedHashMap<>(utilities);
        merged.putAll(renderedUtilities);
        return Map.copyOf(merged);
    }

    private static List<MenuSlot> buildBaseSlots(boolean sharedFooter) {
        Map<Integer, MenuSlot> slots = StandardMenuService.createFilledSlots(StandardMenuService.LIST_ROWS);
        if (sharedFooter) {
            int footerStart = HouseMenuCompiler.footerStart(StandardMenuService.LIST_ROWS);
            StandardMenuService.applySharedFooter(slots, footerStart, null, null);
        }
        return StandardMenuService.orderedSlots(slots, StandardMenuService.LIST_ROWS);
    }

    private static List<MenuSlot> buildBaseSlots(Map<UtilitySlot, MenuItem> utilities, boolean sharedFooter) {
        Map<Integer, MenuSlot> slots = StandardMenuService.createFilledSlots(StandardMenuService.LIST_ROWS);
        if (sharedFooter) {
            int footerStart = HouseMenuCompiler.footerStart(StandardMenuService.LIST_ROWS);
            StandardMenuService.applyUtilities(slots, footerStart, utilities);
            StandardMenuService.applySharedFooter(slots, footerStart, null, null);
        }
        return StandardMenuService.orderedSlots(slots, StandardMenuService.LIST_ROWS);
    }
}
