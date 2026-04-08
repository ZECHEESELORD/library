package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.CanvasMenuBuilder;
import sh.harold.creative.library.menu.ListMenuBuilder;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuService;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.ReactiveCanvasMenuBuilder;
import sh.harold.creative.library.menu.ReactiveListMenuBuilder;
import sh.harold.creative.library.menu.ReactiveListRenderer;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuBuilder;
import sh.harold.creative.library.menu.ReactiveGeometryAction;
import sh.harold.creative.library.menu.ReactiveMenuReducer;
import sh.harold.creative.library.menu.ReactiveMenuRenderer;
import sh.harold.creative.library.menu.ReactiveTabsMenuBuilder;
import sh.harold.creative.library.menu.ReactiveTabsRenderer;
import sh.harold.creative.library.menu.TabsMenuBuilder;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.ArrayList;
import java.util.AbstractSet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class StandardMenuService implements MenuService {

    static final int LIST_ROWS = 6;

    static final int TABS_CONTENT_START = 18;
    static final int TABS_SHARED_CONTENT_END = 44;
    static final int TABS_CUSTOM_CONTENT_END = 53;
    static final int TABS_INDICATOR_ROW_START = 9;
    static final int TABS_NAV_LEFT_SLOT = 0;
    static final int TABS_NAV_RIGHT_SLOT = 8;
    static final int TABS_NAV_FULL_WIDTH = 9;
    static final int TABS_NAV_WINDOW_WIDTH = 7;

    static final int FOOTER_PREVIOUS_OFFSET = 0;
    static final int FOOTER_BACK_OFFSET = 3;
    static final int FOOTER_CLOSE_OFFSET = 4;
    static final int FOOTER_NEXT_OFFSET = 8;

    @Override
    public ListMenuBuilder list() {
        return new DefaultListMenuBuilder();
    }

    @Override
    public TabsMenuBuilder tabs() {
        return new DefaultTabsMenuBuilder();
    }

    @Override
    public CanvasMenuBuilder canvas() {
        return new DefaultCanvasMenuBuilder();
    }

    @Override
    public ReactiveMenuBuilder<Void> reactive() {
        return reactiveCanvas();
    }

    @Override
    public ReactiveCanvasMenuBuilder<Void> reactiveCanvas() {
        return new DefaultReactiveCanvasMenuBuilder<>();
    }

    @Override
    public ReactiveListMenuBuilder<Void> reactiveList() {
        return new DefaultReactiveListMenuBuilder<>();
    }

    @Override
    public ReactiveTabsMenuBuilder<Void> reactiveTabs() {
        return new DefaultReactiveTabsMenuBuilder<>();
    }

    private static final class DefaultListMenuBuilder implements ListMenuBuilder {

        private Component title = Component.text("Menu");
        private final List<MenuItem> items = new ArrayList<>();
        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();

        @Override
        public ListMenuBuilder title(String title) {
            this.title = Component.text(Objects.requireNonNull(title, "title"));
            return this;
        }

        @Override
        public ListMenuBuilder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        @Override
        public ListMenuBuilder utility(UtilitySlot slot, MenuItem item) {
            utilities.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public ListMenuBuilder addItem(MenuItem item) {
            items.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public ListMenuBuilder addItems(Iterable<? extends MenuItem> items) {
            Objects.requireNonNull(items, "items");
            for (MenuItem item : items) {
                addItem(item);
            }
            return this;
        }

        @Override
        public <T> ListMenuBuilder addItems(Iterable<T> items, Function<T, ? extends MenuItem> mapper) {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(mapper, "mapper");
            for (T item : items) {
                addItem(mapper.apply(item));
            }
            return this;
        }

        @Override
        public Menu build() {
            int totalPages = PagedListSupport.pageCount(items.size(), PagedListSupport.PURE_LIST_PAGE_SIZE);
            Set<String> frameIds = new ListFrameIds(totalPages);
            Menu menu = new StandardMenu(title, MenuGeometry.LIST, LIST_ROWS, listFrameId(0), frameIds,
                    frameId -> {
                        int pageIndex = listPageIndex(frameId);
                        return new MenuFrame(listFrameTitle(title, pageIndex, totalPages),
                                buildListPage(pageIndex, totalPages, items, utilities));
                    });
            MenuValidator.validate(menu);
            return menu;
        }
    }

    private static final class DefaultTabsMenuBuilder implements TabsMenuBuilder {

        private Component title = Component.text("Menu");
        private final List<PendingTabGroup> groups = new ArrayList<>();
        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();
        private String defaultTabId;
        private boolean sharedFooter = true;
        private int implicitGroupCount;

        @Override
        public TabsMenuBuilder title(String title) {
            this.title = Component.text(Objects.requireNonNull(title, "title"));
            return this;
        }

        @Override
        public TabsMenuBuilder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        @Override
        public TabsMenuBuilder utility(UtilitySlot slot, MenuItem item) {
            utilities.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public TabsMenuBuilder customFooter() {
            this.sharedFooter = false;
            return this;
        }

        @Override
        public TabsMenuBuilder defaultTab(String tabId) {
            this.defaultTabId = Objects.requireNonNull(tabId, "tabId");
            return this;
        }

        @Override
        public TabsMenuBuilder addGroup(MenuTabGroup group) {
            groups.add(PendingTabGroup.explicit(group));
            return this;
        }

        @Override
        public TabsMenuBuilder addTab(MenuTab tab) {
            PendingTabGroup group = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (group == null || !group.implicit()) {
                group = PendingTabGroup.implicit("implicit:" + implicitGroupCount++);
                groups.add(group);
            }
            group.tabs().add(Objects.requireNonNull(tab, "tab"));
            return this;
        }

        @Override
        public Menu build() {
            List<MenuTabGroup> builtGroups = buildGroups();
            List<FlatTab> flatTabs = flattenTabs(builtGroups);
            validateTabs(flatTabs);
            validateFooterMode(flatTabs);
            validateTabPlacements(flatTabs);

            NavPlan navPlan = buildNavPlan(flatTabs);
            String initialTabId = defaultTabId != null ? defaultTabId : flatTabs.get(0).tab().id();
            int initialNavStart = initialNavStart(flatTabs, navPlan, initialTabId);

            Map<String, FlatTab> tabsById = new LinkedHashMap<>();
            for (FlatTab flatTab : flatTabs) {
                tabsById.put(flatTab.tab().id(), flatTab);
            }
            Set<String> frameIds = new TabFrameIds(tabsById, navPlan);

            String initialFrameId = tabFrameId(initialTabId, initialNavStart, 0);
            Menu menu = new StandardMenu(title, MenuGeometry.TABS, LIST_ROWS, initialFrameId, frameIds,
                    frameId -> {
                        TabFrameRef ref = parseTabFrameId(frameId);
                        FlatTab flatTab = tabsById.get(ref.tabId());
                        if (flatTab == null) {
                            throw new IllegalArgumentException("Unknown tab id: " + ref.tabId());
                        }
                        int totalPages = contentPageCount(flatTab.tab());
                        if (ref.pageIndex() < 0 || ref.pageIndex() >= totalPages) {
                            throw new IllegalArgumentException("Unknown page index " + ref.pageIndex() + " for tab " + ref.tabId());
                        }
                        if (ref.navStart() < 0 || ref.navStart() >= navPlan.windowCount()) {
                            throw new IllegalArgumentException("Unknown nav start " + ref.navStart() + " for tab " + ref.tabId());
                        }
                        return new MenuFrame(title, buildTabPage(flatTab.tab(), ref.pageIndex(), totalPages, utilities, flatTabs,
                                navPlan, ref.navStart(), sharedFooter));
                    });
            MenuValidator.validate(menu);
            return menu;
        }

        private List<MenuTabGroup> buildGroups() {
            List<MenuTabGroup> built = new ArrayList<>();
            for (PendingTabGroup group : groups) {
                if (group.tabs().isEmpty()) {
                    continue;
                }
                built.add(new MenuTabGroup(group.id(), group.tabs()));
            }
            return List.copyOf(built);
        }

        private void validateTabs(List<FlatTab> flatTabs) {
            if (flatTabs.size() < 2) {
                throw new IllegalStateException("Tabs menu requires at least two tabs");
            }
            Set<String> ids = new HashSet<>();
            for (FlatTab flatTab : flatTabs) {
                if (!ids.add(flatTab.tab().id())) {
                    throw new IllegalStateException("Duplicate tab id: " + flatTab.tab().id());
                }
            }
            if (defaultTabId != null && flatTabs.stream().noneMatch(tab -> tab.tab().id().equals(defaultTabId))) {
                throw new IllegalStateException("Default tab id does not exist: " + defaultTabId);
            }
        }

        private void validateFooterMode(List<FlatTab> flatTabs) {
            if (sharedFooter) {
                return;
            }
            if (!utilities.isEmpty()) {
                throw new IllegalStateException("Custom-footer tabs may not use shared footer utility slots");
            }
            for (FlatTab flatTab : flatTabs) {
                if (flatTab.tab().content() instanceof MenuTabContent.ListContent) {
                    throw new IllegalStateException("List tab content requires the shared footer");
                }
            }
        }

        private void validateTabPlacements(List<FlatTab> flatTabs) {
            for (FlatTab flatTab : flatTabs) {
                if (!(flatTab.tab().content() instanceof MenuTabContent.CanvasContent canvas)) {
                    continue;
                }
                for (Integer slot : canvas.placements().keySet()) {
                    if (slot < TABS_CONTENT_START) {
                        throw new IllegalArgumentException("Tab canvas slot " + slot + " collides with reserved tab chrome");
                    }
                    if (sharedFooter && slot > TABS_SHARED_CONTENT_END) {
                        throw new IllegalArgumentException("Shared-footer tabs may not place content in row 5");
                    }
                    if (!sharedFooter && slot > TABS_CUSTOM_CONTENT_END) {
                        throw new IllegalArgumentException("Tab canvas slot " + slot + " is outside the tab menu");
                    }
                }
            }
        }
    }

    private static final class DefaultCanvasMenuBuilder implements CanvasMenuBuilder {

        private Component title = Component.text("Menu");
        private final Map<Integer, MenuItem> placed = new LinkedHashMap<>();
        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();
        private int rows = 6;

        @Override
        public CanvasMenuBuilder title(String title) {
            this.title = Component.text(Objects.requireNonNull(title, "title"));
            return this;
        }

        @Override
        public CanvasMenuBuilder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        @Override
        public CanvasMenuBuilder rows(int rows) {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("rows must be between 1 and 6");
            }
            this.rows = rows;
            return this;
        }

        @Override
        public CanvasMenuBuilder utility(UtilitySlot slot, MenuItem item) {
            utilities.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public CanvasMenuBuilder place(int slot, MenuItem item) {
            if (slot < 0 || slot >= rows * 9) {
                throw new IllegalArgumentException("slot " + slot + " is outside a " + rows + "-row canvas");
            }
            placed.put(slot, Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public Menu build() {
            Menu menu = new StandardMenu(title, MenuGeometry.CANVAS, rows, "canvas:0", Set.of("canvas:0"),
                    frameId -> {
                        if (!"canvas:0".equals(frameId)) {
                            throw new IllegalArgumentException("Unknown canvas frame: " + frameId);
                        }
                        return new MenuFrame(title, buildCanvasPage(rows, placed, utilities));
                    });
            MenuValidator.validate(menu);
            return menu;
        }
    }

    private static final class DefaultReactiveCanvasMenuBuilder<S> implements ReactiveCanvasMenuBuilder<S> {

        private int rows = 6;
        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();
        private boolean fillWithBlackPane = true;
        private long tickIntervalTicks;
        private Supplier<?> stateFactory = () -> null;
        private ReactiveMenuRenderer<?> renderer;
        private ReactiveMenuReducer<?> reducer;

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReactiveCanvasMenuBuilder<T> state(T state) {
            this.stateFactory = () -> state;
            return (ReactiveCanvasMenuBuilder<T>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReactiveCanvasMenuBuilder<T> stateFactory(Supplier<? extends T> stateFactory) {
            this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
            return (ReactiveCanvasMenuBuilder<T>) this;
        }

        @Override
        public ReactiveCanvasMenuBuilder<S> rows(int rows) {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("rows must be between 1 and 6");
            }
            this.rows = rows;
            return this;
        }

        @Override
        public ReactiveCanvasMenuBuilder<S> utility(UtilitySlot slot, MenuItem item) {
            utilities.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public ReactiveCanvasMenuBuilder<S> fillWithBlackPane(boolean fillWithBlackPane) {
            this.fillWithBlackPane = fillWithBlackPane;
            return this;
        }

        @Override
        public ReactiveCanvasMenuBuilder<S> tickEvery(long tickIntervalTicks) {
            if (tickIntervalTicks <= 0L) {
                throw new IllegalArgumentException("tickIntervalTicks must be greater than zero");
            }
            this.tickIntervalTicks = tickIntervalTicks;
            return this;
        }

        @Override
        public ReactiveCanvasMenuBuilder<S> render(ReactiveMenuRenderer<? super S> renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            return this;
        }

        @Override
        public ReactiveCanvasMenuBuilder<S> reduce(ReactiveMenuReducer<? super S> reducer) {
            this.reducer = Objects.requireNonNull(reducer, "reducer");
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ReactiveMenu build() {
            if (renderer == null) {
                throw new IllegalStateException("Reactive menu requires a renderer");
            }
            if (reducer == null) {
                throw new IllegalStateException("Reactive menu requires a reducer");
            }
            int footerStart = HouseMenuCompiler.footerStart(rows);
            validateUtilitySlots(utilities, footerStart, reservedCanvasFooterSlots(footerStart));
            return new StandardReactiveMenu<>(rows, utilities, fillWithBlackPane, tickIntervalTicks,
                    (Supplier<? extends S>) stateFactory,
                    (ReactiveMenuRenderer<? super S>) renderer,
                    (ReactiveMenuReducer<? super S>) reducer);
        }
    }

    private static final class DefaultReactiveListMenuBuilder<S> implements ReactiveListMenuBuilder<S> {

        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();
        private long tickIntervalTicks;
        private Supplier<?> stateFactory = () -> null;
        private ReactiveListRenderer<?> renderer;
        private ReactiveMenuReducer<?> reducer;

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReactiveListMenuBuilder<T> state(T state) {
            this.stateFactory = () -> state;
            return (ReactiveListMenuBuilder<T>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReactiveListMenuBuilder<T> stateFactory(Supplier<? extends T> stateFactory) {
            this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
            return (ReactiveListMenuBuilder<T>) this;
        }

        @Override
        public ReactiveListMenuBuilder<S> utility(UtilitySlot slot, MenuItem item) {
            utilities.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public ReactiveListMenuBuilder<S> tickEvery(long tickIntervalTicks) {
            if (tickIntervalTicks <= 0L) {
                throw new IllegalArgumentException("tickIntervalTicks must be greater than zero");
            }
            this.tickIntervalTicks = tickIntervalTicks;
            return this;
        }

        @Override
        public ReactiveListMenuBuilder<S> render(ReactiveListRenderer<? super S> renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            return this;
        }

        @Override
        public ReactiveListMenuBuilder<S> reduce(ReactiveMenuReducer<? super S> reducer) {
            this.reducer = Objects.requireNonNull(reducer, "reducer");
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ReactiveMenu build() {
            if (renderer == null) {
                throw new IllegalStateException("Reactive list menu requires a renderer");
            }
            if (reducer == null) {
                throw new IllegalStateException("Reactive list menu requires a reducer");
            }
            int footerStart = HouseMenuCompiler.footerStart(LIST_ROWS);
            validateUtilitySlots(utilities, footerStart, reservedCanvasFooterSlots(footerStart));
            return new StandardReactiveListMenu<>(utilities, tickIntervalTicks,
                    (Supplier<? extends S>) stateFactory,
                    (ReactiveListRenderer<? super S>) renderer,
                    (ReactiveMenuReducer<? super S>) reducer);
        }
    }

    private static final class DefaultReactiveTabsMenuBuilder<S> implements ReactiveTabsMenuBuilder<S> {

        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();
        private boolean sharedFooter = true;
        private long tickIntervalTicks;
        private Supplier<?> stateFactory = () -> null;
        private ReactiveTabsRenderer<?> renderer;
        private ReactiveMenuReducer<?> reducer;

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReactiveTabsMenuBuilder<T> state(T state) {
            this.stateFactory = () -> state;
            return (ReactiveTabsMenuBuilder<T>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReactiveTabsMenuBuilder<T> stateFactory(Supplier<? extends T> stateFactory) {
            this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
            return (ReactiveTabsMenuBuilder<T>) this;
        }

        @Override
        public ReactiveTabsMenuBuilder<S> utility(UtilitySlot slot, MenuItem item) {
            utilities.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public ReactiveTabsMenuBuilder<S> customFooter() {
            this.sharedFooter = false;
            return this;
        }

        @Override
        public ReactiveTabsMenuBuilder<S> tickEvery(long tickIntervalTicks) {
            if (tickIntervalTicks <= 0L) {
                throw new IllegalArgumentException("tickIntervalTicks must be greater than zero");
            }
            this.tickIntervalTicks = tickIntervalTicks;
            return this;
        }

        @Override
        public ReactiveTabsMenuBuilder<S> render(ReactiveTabsRenderer<? super S> renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            return this;
        }

        @Override
        public ReactiveTabsMenuBuilder<S> reduce(ReactiveMenuReducer<? super S> reducer) {
            this.reducer = Objects.requireNonNull(reducer, "reducer");
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ReactiveMenu build() {
            if (renderer == null) {
                throw new IllegalStateException("Reactive tabs menu requires a renderer");
            }
            if (reducer == null) {
                throw new IllegalStateException("Reactive tabs menu requires a reducer");
            }
            if (!sharedFooter && !utilities.isEmpty()) {
                throw new IllegalStateException("Custom-footer tabs may not use shared footer utility slots");
            }
            return new StandardReactiveTabsMenu<>(utilities, sharedFooter, tickIntervalTicks,
                    (Supplier<? extends S>) stateFactory,
                    (ReactiveTabsRenderer<? super S>) renderer,
                    (ReactiveMenuReducer<? super S>) reducer);
        }
    }

    private static List<MenuSlot> buildListPage(
            int pageIndex,
            int totalPages,
            List<MenuItem> items,
            Map<UtilitySlot, MenuItem> utilities
    ) {
        Map<Integer, MenuSlot> slots = createFilledSlots(LIST_ROWS);
        clearListContentArea(slots);
        int footerStart = HouseMenuCompiler.footerStart(LIST_ROWS);
        validateUtilitySlots(utilities, footerStart,
                reservedSharedFooterSlots(footerStart, totalPages > 1 && pageIndex > 0, totalPages > 1 && pageIndex + 1 < totalPages));
        int firstItem = PagedListSupport.firstItemIndex(pageIndex, PagedListSupport.PURE_LIST_PAGE_SIZE);
        int lastItem = PagedListSupport.lastItemExclusive(pageIndex, items.size(), PagedListSupport.PURE_LIST_PAGE_SIZE);
        int slotIndex = 0;
        for (int i = firstItem; i < lastItem; i++) {
            int slot = PagedListSupport.PURE_LIST_CONTENT_SLOTS.get(slotIndex++);
            slots.put(slot, HouseMenuCompiler.compile(slot, items.get(i)));
        }
        applyUtilities(slots, footerStart, utilities);
        applySharedFooter(slots, footerStart,
                pageIndex > 0 ? listFrameId(pageIndex - 1) : null,
                pageIndex + 1 < totalPages ? listFrameId(pageIndex + 1) : null);
        return orderedSlots(slots, LIST_ROWS);
    }

    private static void clearListContentArea(Map<Integer, MenuSlot> slots) {
        for (int slot : PagedListSupport.PURE_LIST_CONTENT_SLOTS) {
            slots.put(slot, empty(slot));
        }
    }

    private static List<MenuSlot> buildTabPage(
            MenuTab activeTab,
            int pageIndex,
            int totalPages,
            Map<UtilitySlot, MenuItem> utilities,
            List<FlatTab> flatTabs,
            NavPlan navPlan,
            int navStart,
            boolean sharedFooter
    ) {
        Map<Integer, MenuSlot> slots = createFilledSlots(LIST_ROWS);
        renderTabChrome(slots, activeTab, flatTabs, navPlan, navStart, pageIndex);

        if (activeTab.content() instanceof MenuTabContent.ListContent list) {
            renderTabListContent(slots, list.items(), pageIndex);
        } else if (activeTab.content() instanceof MenuTabContent.CanvasContent canvas) {
            renderTabCanvasContent(slots, canvas, sharedFooter);
        }

        if (sharedFooter) {
            int footerStart = HouseMenuCompiler.footerStart(LIST_ROWS);
            validateUtilitySlots(utilities, footerStart,
                    reservedSharedFooterSlots(footerStart, totalPages > 1 && pageIndex > 0, totalPages > 1 && pageIndex + 1 < totalPages));
            applyUtilities(slots, footerStart, utilities);
            applySharedFooter(slots, footerStart,
                    pageIndex > 0 ? tabFrameId(activeTab.id(), navStart, pageIndex - 1) : null,
                    pageIndex + 1 < totalPages ? tabFrameId(activeTab.id(), navStart, pageIndex + 1) : null);
        }

        return orderedSlots(slots, LIST_ROWS);
    }

    static void renderTabChrome(Map<Integer, MenuSlot> slots, MenuTab activeTab, List<FlatTab> flatTabs, NavPlan navPlan,
                                        int navStart, int pageIndex) {
        NavWindow window = navPlan.window(navStart);
        List<PositionedTab> positioned = positionTabs(flatTabs, window, navPlan.overflow());
        boolean activeVisible = false;
        for (PositionedTab position : positioned) {
            MenuTab tab = position.flatTab().tab();
            boolean active = tab.id().equals(activeTab.id());
            activeVisible |= active;
            slots.put(position.slot(), tabButton(position.slot(), tab, active, navStart));
        }
        for (PositionedTab position : positioned) {
            boolean active = position.flatTab().tab().id().equals(activeTab.id());
            slots.put(position.slot() + TABS_INDICATOR_ROW_START, tabIndicator(position.slot() + TABS_INDICATOR_ROW_START, active));
        }
        if (navPlan.overflow()) {
            slots.put(TABS_NAV_LEFT_SLOT, navArrow(TABS_NAV_LEFT_SLOT, "Previous Tab",
                    Math.max(0, navStart - 1) + 1,
                    navStart > 0 ? tabFrameId(activeTab.id(), navStart - 1, pageIndex) : null,
                    navStart > 0 ? tabFrameId(activeTab.id(), 0, pageIndex) : null));
            slots.put(TABS_NAV_RIGHT_SLOT, navArrow(TABS_NAV_RIGHT_SLOT, "Next Tab",
                    Math.min(navPlan.lastStart(), navStart + 1) + 1,
                    navStart < navPlan.lastStart() ? tabFrameId(activeTab.id(), navStart + 1, pageIndex) : null,
                    navStart < navPlan.lastStart() ? tabFrameId(activeTab.id(), navPlan.lastStart(), pageIndex) : null));
        }
        if (!activeVisible) {
            // Keep the nav row intentionally neutral when the active tab is off-screen after strip scrolling.
        }
    }

    private static void renderTabListContent(Map<Integer, MenuSlot> slots, List<MenuItem> items, int pageIndex) {
        clearTabListContentArea(slots);
        int firstItem = PagedListSupport.firstItemIndex(pageIndex, PagedListSupport.TAB_LIST_PAGE_SIZE);
        int lastItem = PagedListSupport.lastItemExclusive(pageIndex, items.size(), PagedListSupport.TAB_LIST_PAGE_SIZE);
        int slotIndex = 0;
        for (int i = firstItem; i < lastItem; i++) {
            int slot = PagedListSupport.TAB_LIST_CONTENT_SLOTS.get(slotIndex++);
            slots.put(slot, HouseMenuCompiler.compile(slot, items.get(i)));
        }
    }

    private static void clearTabListContentArea(Map<Integer, MenuSlot> slots) {
        for (int slot : PagedListSupport.TAB_LIST_CONTENT_SLOTS) {
            slots.put(slot, empty(slot));
        }
    }

    private static void renderTabCanvasContent(Map<Integer, MenuSlot> slots, MenuTabContent.CanvasContent canvas, boolean sharedFooter) {
        if (!canvas.fillWithBlackPane()) {
            clearTabCanvasArea(slots, sharedFooter);
        }
        for (Map.Entry<Integer, MenuItem> entry : canvas.placements().entrySet()) {
            slots.put(entry.getKey(), HouseMenuCompiler.compile(entry.getKey(), entry.getValue()));
        }
    }

    private static void clearTabCanvasArea(Map<Integer, MenuSlot> slots, boolean sharedFooter) {
        int end = sharedFooter ? TABS_SHARED_CONTENT_END : TABS_CUSTOM_CONTENT_END;
        for (int slot = TABS_CONTENT_START; slot <= end; slot++) {
            slots.put(slot, empty(slot));
        }
    }

    static void applySharedFooter(
            Map<Integer, MenuSlot> slots,
            int footerStart,
            String previousFrameId,
            String nextFrameId
    ) {
        if (previousFrameId != null) {
            slots.put(footerStart + FOOTER_PREVIOUS_OFFSET,
                    navigationButton(footerStart + FOOTER_PREVIOUS_OFFSET, "Previous Page", pageNumberFromFrameId(previousFrameId),
                            MenuIcon.vanilla("arrow"),
                            Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.PREVIOUS_PAGE, new MenuSlotAction.OpenFrame(previousFrameId)))));
        }
        if (nextFrameId != null) {
            slots.put(footerStart + FOOTER_NEXT_OFFSET,
                    navigationButton(footerStart + FOOTER_NEXT_OFFSET, "Next Page", pageNumberFromFrameId(nextFrameId),
                            MenuIcon.vanilla("arrow"),
                            Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.NEXT_PAGE, new MenuSlotAction.OpenFrame(nextFrameId)))));
        }
        slots.put(footerStart + FOOTER_CLOSE_OFFSET,
                simpleButton(footerStart + FOOTER_CLOSE_OFFSET, "Close", NamedTextColor.RED, MenuIcon.vanilla("barrier"),
                        Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.CLOSE, new MenuSlotAction.Close()))));
    }

    private static MenuSlot tabButton(int slot, MenuTab tab, boolean active, int navStart) {
        Map<MenuClick, MenuInteraction> interactions = Map.of(
                MenuClick.LEFT, MenuInteraction.of(ActionVerb.SWITCH_TAB, new MenuSlotAction.OpenFrame(tabFrameId(tab.id(), navStart, 0))));
        if (tab.secondary() == null && tab.blocks().isEmpty()) {
            return chromeButton(slot, tab.name(), tab.icon(), interactions, active || tab.glow());
        }
        return HouseMenuCompiler.compile(slot, tab.icon(), tab.name(), tab.secondary(), tab.blocks(), active || tab.glow(), interactions, false, 1);
    }

    static MenuSlot tabIndicator(int slot, boolean active) {
        return new MenuSlot(slot,
                MenuIcon.vanilla(active ? "lime_stained_glass_pane" : "gray_stained_glass_pane"),
                Component.text(" "),
                List.of(),
                active,
                Map.of());
    }

    private static MenuSlot navArrow(int slot, String title, int pageNumber, String leftFrameId, String rightFrameId) {
        if (leftFrameId == null && rightFrameId == null) {
            return navigationButton(slot, title, pageNumber, MenuIcon.vanilla("arrow"), Map.of());
        }
        Map<MenuClick, MenuInteraction> interactions = new EnumMap<>(MenuClick.class);
        if (leftFrameId != null) {
            ActionVerb verb = title.startsWith("Previous") ? ActionVerb.PREVIOUS_PAGE : ActionVerb.NEXT_PAGE;
            interactions.put(MenuClick.LEFT, MenuInteraction.of(verb,
                    title.startsWith("Previous") ? "browse previous tabs" : "browse next tabs",
                    new MenuSlotAction.OpenFrame(leftFrameId)));
        }
        if (rightFrameId != null) {
            interactions.put(MenuClick.RIGHT, MenuInteraction.of(ActionVerb.BROWSE,
                    title.startsWith("Previous") ? "jump to first tabs" : "jump to last tabs",
                    new MenuSlotAction.OpenFrame(rightFrameId),
                    sh.harold.creative.library.sound.SoundCueKeys.MENU_SCROLL));
        }
        return navigationButton(slot, title, pageNumber, MenuIcon.vanilla("arrow"), interactions);
    }

    static MenuSlot filler(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("black_stained_glass_pane"), Component.text(" "),
                List.of(), false, Map.of());
    }

    static MenuSlot empty(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("air"), Component.empty(), List.of(), false, Map.of());
    }

    static MenuSlot simpleButton(int slot, String title, NamedTextColor color, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions) {
        return new MenuSlot(slot, icon,
                Component.text(title, color).decoration(TextDecoration.ITALIC, false),
                List.of(),
                false,
                interactions);
    }

    static MenuSlot navigationButton(int slot, String title, int pageNumber, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions) {
        return new MenuSlot(slot, icon,
                Component.text(title, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Page " + pageNumber, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)),
                false,
                interactions);
    }

    static MenuSlot chromeButton(int slot, String title, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions) {
        return chromeButton(slot, title, icon, interactions, false);
    }

    static MenuSlot chromeButton(int slot, String title, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions, boolean glow) {
        return chromeButton(slot, Component.text(title), icon, interactions, glow);
    }

    static MenuSlot chromeButton(int slot, Component title, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions, boolean glow) {
        return new MenuSlot(slot, icon, title.decoration(TextDecoration.ITALIC, false),
                chromeLore(title, interactions), glow, interactions);
    }

    private static List<Component> chromeLore(Component title, Map<MenuClick, MenuInteraction> interactions) {
        String plainTitle = plain(title);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(plainTitle, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (!interactions.isEmpty()) {
            lore.add(Component.empty());
            MenuInteraction left = interactions.get(MenuClick.LEFT);
            if (left != null) {
                lore.add(promptLine("CLICK", left.promptLabel(), NamedTextColor.YELLOW));
            }
            MenuInteraction right = interactions.get(MenuClick.RIGHT);
            if (right != null) {
                lore.add(promptLine("RIGHT CLICK", right.promptLabel(), NamedTextColor.AQUA));
            }
        }
        return List.copyOf(lore);
    }

    private static Component promptLine(String clickLabel, String promptLabel, NamedTextColor color) {
        return Component.text()
                .append(Component.text(clickLabel, color, TextDecoration.BOLD))
                .append(Component.text(" to " + emphaticPromptLabel(promptLabel), color))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static String emphaticPromptLabel(String promptLabel) {
        return promptLabel.endsWith("!") ? promptLabel : promptLabel + "!";
    }

    static void applyUtilities(Map<Integer, MenuSlot> slots, int footerStart, Map<UtilitySlot, MenuItem> utilities) {
        for (Map.Entry<UtilitySlot, MenuItem> entry : utilities.entrySet()) {
            int slot = entry.getKey().resolveSlot(footerStart);
            slots.put(slot, HouseMenuCompiler.compile(slot, entry.getValue()));
        }
    }

    static Map<Integer, MenuSlot> createFilledSlots(int rows) {
        Map<Integer, MenuSlot> slots = new LinkedHashMap<>();
        for (int slot = 0; slot < rows * 9; slot++) {
            slots.put(slot, filler(slot));
        }
        return slots;
    }

    private static List<MenuSlot> buildCanvasPage(
            int rows,
            Map<Integer, MenuItem> placed,
            Map<UtilitySlot, MenuItem> utilities
    ) {
        Map<Integer, MenuSlot> slots = createFilledSlots(rows);
        int footerStart = HouseMenuCompiler.footerStart(rows);
        validateUtilitySlots(utilities, footerStart, reservedCanvasFooterSlots(footerStart));
        for (UtilitySlot slot : utilities.keySet()) {
            int reserved = slot.resolveSlot(footerStart);
            if (placed.containsKey(reserved)) {
                throw new IllegalArgumentException("Placed item collides with utility chrome slot " + reserved);
            }
        }
        for (Map.Entry<Integer, MenuItem> entry : placed.entrySet()) {
            if (entry.getKey() >= footerStart && (entry.getKey() == footerStart + FOOTER_BACK_OFFSET
                    || entry.getKey() == footerStart + FOOTER_CLOSE_OFFSET
                    || entry.getKey() == footerStart + FOOTER_NEXT_OFFSET)) {
                throw new IllegalArgumentException("Placed items may not overwrite reserved canvas chrome slots");
            }
            slots.put(entry.getKey(), HouseMenuCompiler.compile(entry.getKey(), entry.getValue()));
        }
        applyUtilities(slots, footerStart, utilities);
        slots.put(footerStart + FOOTER_CLOSE_OFFSET,
                simpleButton(footerStart + FOOTER_CLOSE_OFFSET, "Close", NamedTextColor.RED, MenuIcon.vanilla("barrier"),
                        Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.CLOSE, new MenuSlotAction.Close()))));
        return orderedSlots(slots, rows);
    }

    static List<MenuSlot> orderedSlots(Map<Integer, MenuSlot> slots, int rows) {
        List<MenuSlot> ordered = new ArrayList<>();
        for (int slot = 0; slot < rows * 9; slot++) {
            ordered.add(slots.get(slot));
        }
        return List.copyOf(ordered);
    }

    static List<FlatTab> flattenTabs(List<MenuTabGroup> groups) {
        List<FlatTab> flattened = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            MenuTabGroup group = groups.get(groupIndex);
            for (MenuTab tab : group.tabs()) {
                flattened.add(new FlatTab(tab, groupIndex));
            }
        }
        return List.copyOf(flattened);
    }

    static NavPlan buildNavPlan(List<FlatTab> flatTabs) {
        int totalRenderedWidth = renderedWidth(flatTabs, 0, flatTabs.size() - 1);
        if (totalRenderedWidth <= TABS_NAV_FULL_WIDTH) {
            return new NavPlan(false, List.of(new NavWindow(0, flatTabs.size() - 1, totalRenderedWidth)));
        }
        List<NavWindow> allWindows = new ArrayList<>();
        for (int start = 0; start < flatTabs.size(); start++) {
            allWindows.add(windowForStart(flatTabs, start, TABS_NAV_WINDOW_WIDTH));
        }
        int lastStart = 0;
        for (int start = 0; start < allWindows.size(); start++) {
            if (allWindows.get(start).endIndex() == flatTabs.size() - 1) {
                lastStart = start;
                break;
            }
        }
        return new NavPlan(true, List.copyOf(allWindows.subList(0, lastStart + 1)));
    }

    private static NavWindow windowForStart(List<FlatTab> flatTabs, int startIndex, int capacity) {
        int width = 0;
        int endIndex = startIndex - 1;
        for (int index = startIndex; index < flatTabs.size(); index++) {
            int addition = 1;
            if (index > startIndex && flatTabs.get(index - 1).groupIndex() != flatTabs.get(index).groupIndex()) {
                addition++;
            }
            if (width + addition > capacity) {
                break;
            }
            width += addition;
            endIndex = index;
        }
        return new NavWindow(startIndex, endIndex, width);
    }

    private static int initialNavStart(List<FlatTab> flatTabs, NavPlan navPlan, String tabId) {
        if (!navPlan.overflow()) {
            return 0;
        }
        int tabIndex = tabIndex(flatTabs, tabId);
        for (int start = 0; start < navPlan.windowCount(); start++) {
            NavWindow window = navPlan.window(start);
            if (window.startIndex() <= tabIndex && tabIndex <= window.endIndex()) {
                return start;
            }
        }
        return 0;
    }

    private static int tabIndex(List<FlatTab> flatTabs, String tabId) {
        for (int index = 0; index < flatTabs.size(); index++) {
            if (flatTabs.get(index).tab().id().equals(tabId)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unknown tab id: " + tabId);
    }

    static List<PositionedTab> positionTabs(List<FlatTab> flatTabs, NavWindow window, boolean overflow) {
        List<PositionedTab> positioned = new ArrayList<>();
        int availableWidth = overflow ? TABS_NAV_WINDOW_WIDTH : TABS_NAV_FULL_WIDTH;
        int startSlot = (overflow ? 1 : 0) + Math.max(0, (availableWidth - window.renderedWidth()) / 2);
        int slot = startSlot;
        for (int index = window.startIndex(); index <= window.endIndex(); index++) {
            if (index > window.startIndex() && flatTabs.get(index - 1).groupIndex() != flatTabs.get(index).groupIndex()) {
                slot++;
            }
            positioned.add(new PositionedTab(flatTabs.get(index), slot));
            slot++;
        }
        return List.copyOf(positioned);
    }

    private static int renderedWidth(List<FlatTab> flatTabs, int startIndex, int endIndex) {
        int width = 0;
        for (int index = startIndex; index <= endIndex; index++) {
            width++;
            if (index > startIndex && flatTabs.get(index - 1).groupIndex() != flatTabs.get(index).groupIndex()) {
                width++;
            }
        }
        return width;
    }

    static int contentPageCount(MenuTab tab) {
        if (tab.content() instanceof MenuTabContent.ListContent list) {
            return PagedListSupport.pageCount(list.items().size(), PagedListSupport.TAB_LIST_PAGE_SIZE);
        }
        return 1;
    }

    private static int listPageIndex(String frameId) {
        if (!frameId.startsWith("page:")) {
            throw new IllegalArgumentException("Unknown list frame id: " + frameId);
        }
        return Integer.parseInt(frameId.substring(5));
    }

    private static String listFrameId(int pageIndex) {
        return "page:" + pageIndex;
    }

    private static String tabFrameId(String tabId, int navStart, int pageIndex) {
        return "tab:" + tabId + ":nav:" + navStart + ":page:" + pageIndex;
    }

    private static TabFrameRef parseTabFrameId(String frameId) {
        if (!frameId.startsWith("tab:")) {
            throw new IllegalArgumentException("Unknown tab frame id: " + frameId);
        }
        int navMarker = frameId.indexOf(":nav:");
        int pageMarker = frameId.indexOf(":page:");
        if (navMarker < 0 || pageMarker < 0 || navMarker <= 4 || pageMarker <= navMarker + 5) {
            throw new IllegalArgumentException("Malformed tab frame id: " + frameId);
        }
        String tabId = frameId.substring(4, navMarker);
        int navStart = Integer.parseInt(frameId.substring(navMarker + 5, pageMarker));
        int pageIndex = Integer.parseInt(frameId.substring(pageMarker + 6));
        return new TabFrameRef(tabId, navStart, pageIndex);
    }

    private static Component listFrameTitle(Component title, int pageIndex, int totalPages) {
        return PagedListSupport.decorateTitle(title, pageIndex, totalPages);
    }

    private static int pageNumberFromFrameId(String frameId) {
        int marker = frameId.lastIndexOf(":page:");
        if (marker >= 0) {
            return Integer.parseInt(frameId.substring(marker + 6)) + 1;
        }
        if (frameId.startsWith("page:")) {
            return Integer.parseInt(frameId.substring(5)) + 1;
        }
        throw new IllegalArgumentException("Frame id does not contain a page index: " + frameId);
    }

    private static String plain(Component component) {
        return ComponentText.flatten(component);
    }

    static void validateUtilitySlots(Map<UtilitySlot, MenuItem> utilities, int footerStart, Set<Integer> reserved) {
        for (UtilitySlot slot : utilities.keySet()) {
            int resolved = slot.resolveSlot(footerStart);
            if (reserved.contains(resolved)) {
                throw new IllegalArgumentException("Utility slot " + slot + " collides with reserved house chrome");
            }
        }
    }

    static Set<Integer> reservedSharedFooterSlots(int footerStart, boolean hasPrevious, boolean hasNext) {
        Set<Integer> reserved = new HashSet<>();
        if (hasPrevious) {
            reserved.add(footerStart + FOOTER_PREVIOUS_OFFSET);
        }
        reserved.add(footerStart + FOOTER_BACK_OFFSET);
        if (hasNext) {
            reserved.add(footerStart + FOOTER_NEXT_OFFSET);
        }
        reserved.add(footerStart + FOOTER_CLOSE_OFFSET);
        return reserved;
    }

    static Set<Integer> reservedCanvasFooterSlots(int footerStart) {
        Set<Integer> reserved = new HashSet<>();
        reserved.add(footerStart + FOOTER_BACK_OFFSET);
        reserved.add(footerStart + FOOTER_CLOSE_OFFSET);
        return reserved;
    }

    record FlatTab(MenuTab tab, int groupIndex) {
    }

    record NavWindow(int startIndex, int endIndex, int renderedWidth) {
    }

    record NavPlan(boolean overflow, List<NavWindow> windows) {

        int windowCount() {
            return windows.size();
        }

        int lastStart() {
            return windows.size() - 1;
        }

        NavWindow window(int startIndex) {
            return windows.get(startIndex);
        }
    }

    record PositionedTab(FlatTab flatTab, int slot) {
    }

    private record TabFrameRef(String tabId, int navStart, int pageIndex) {
    }

    private static final class PendingTabGroup {

        private final String id;
        private final boolean implicit;
        private final List<MenuTab> tabs;

        private PendingTabGroup(String id, boolean implicit, List<MenuTab> tabs) {
            this.id = id;
            this.implicit = implicit;
            this.tabs = tabs;
        }

        static PendingTabGroup explicit(MenuTabGroup group) {
            return new PendingTabGroup(group.id(), false, new ArrayList<>(group.tabs()));
        }

        static PendingTabGroup implicit(String id) {
            return new PendingTabGroup(id, true, new ArrayList<>());
        }

        String id() {
            return id;
        }

        boolean implicit() {
            return implicit;
        }

        List<MenuTab> tabs() {
            return tabs;
        }
    }

    private static final class ListFrameIds extends AbstractSet<String> {

        private final int totalPages;

        private ListFrameIds(int totalPages) {
            this.totalPages = totalPages;
        }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<>() {
                private int pageIndex;

                @Override
                public boolean hasNext() {
                    return pageIndex < totalPages;
                }

                @Override
                public String next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return listFrameId(pageIndex++);
                }
            };
        }

        @Override
        public int size() {
            return totalPages;
        }

        @Override
        public boolean contains(Object object) {
            if (!(object instanceof String frameId)) {
                return false;
            }
            try {
                int pageIndex = listPageIndex(frameId);
                return pageIndex >= 0 && pageIndex < totalPages;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
    }

    private static final class TabFrameIds extends AbstractSet<String> {

        private final Map<String, FlatTab> tabsById;
        private final NavPlan navPlan;
        private final List<String> tabIds;
        private final Map<String, Integer> pageCounts;
        private final int size;

        private TabFrameIds(Map<String, FlatTab> tabsById, NavPlan navPlan) {
            this.tabsById = Map.copyOf(tabsById);
            this.navPlan = navPlan;
            this.tabIds = List.copyOf(tabsById.keySet());
            Map<String, Integer> pageCounts = new LinkedHashMap<>();
            int size = 0;
            for (Map.Entry<String, FlatTab> entry : this.tabsById.entrySet()) {
                int pageCount = contentPageCount(entry.getValue().tab());
                pageCounts.put(entry.getKey(), pageCount);
                size += pageCount * navPlan.windowCount();
            }
            this.pageCounts = Map.copyOf(pageCounts);
            this.size = size;
        }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<>() {
                private int tabIndex;
                private int navStart;
                private int pageIndex;

                @Override
                public boolean hasNext() {
                    return tabIndex < tabIds.size();
                }

                @Override
                public String next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    String tabId = tabIds.get(tabIndex);
                    String frameId = tabFrameId(tabId, navStart, pageIndex);
                    advance(tabId);
                    return frameId;
                }

                private void advance(String tabId) {
                    int totalPages = pageCounts.get(tabId);
                    pageIndex++;
                    if (pageIndex >= totalPages) {
                        pageIndex = 0;
                        navStart++;
                        if (navStart >= navPlan.windowCount()) {
                            navStart = 0;
                            tabIndex++;
                        }
                    }
                }
            };
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object object) {
            if (!(object instanceof String frameId)) {
                return false;
            }
            try {
                TabFrameRef ref = parseTabFrameId(frameId);
                FlatTab flatTab = tabsById.get(ref.tabId());
                if (flatTab == null) {
                    return false;
                }
                Integer pageCount = pageCounts.get(ref.tabId());
                return ref.navStart() >= 0 && ref.navStart() < navPlan.windowCount()
                        && ref.pageIndex() >= 0 && ref.pageIndex() < pageCount;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
    }
}
