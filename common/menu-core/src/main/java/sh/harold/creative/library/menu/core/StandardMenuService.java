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
import sh.harold.creative.library.menu.TabsMenuBuilder;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class StandardMenuService implements MenuService {

    private static final int LIST_ROWS = 6;
    private static final List<Integer> LIST_CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final int LIST_CONTENT_SIZE = LIST_CONTENT_SLOTS.size();

    private static final int TABS_CONTENT_START = 18;
    private static final int TABS_SHARED_CONTENT_END = 44;
    private static final int TABS_CUSTOM_CONTENT_END = 53;
    private static final int TABS_INDICATOR_ROW_START = 9;
    private static final int TABS_NAV_LEFT_SLOT = 0;
    private static final int TABS_NAV_RIGHT_SLOT = 8;
    private static final int TABS_NAV_FULL_WIDTH = 9;
    private static final int TABS_NAV_WINDOW_WIDTH = 7;
    private static final List<Integer> TABS_LIST_CONTENT_SLOTS = List.of(
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final int TABS_LIST_CONTENT_SIZE = TABS_LIST_CONTENT_SLOTS.size();

    private static final int FOOTER_PREVIOUS_OFFSET = 0;
    private static final int FOOTER_BACK_OFFSET = 3;
    private static final int FOOTER_CLOSE_OFFSET = 4;
    private static final int FOOTER_NEXT_OFFSET = 8;

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
            int totalPages = Math.max(1, (items.size() + LIST_CONTENT_SIZE - 1) / LIST_CONTENT_SIZE);
            Map<String, MenuFrame> frames = new LinkedHashMap<>();
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                frames.put(listFrameId(pageIndex), new MenuFrame(listFrameTitle(title, pageIndex, totalPages),
                        buildListPage(pageIndex, totalPages, items, utilities)));
            }
            Menu menu = new StandardMenu(title, MenuGeometry.LIST, LIST_ROWS, listFrameId(0), frames);
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

            Map<String, MenuFrame> frames = new LinkedHashMap<>();
            for (FlatTab flatTab : flatTabs) {
                int totalPages = contentPageCount(flatTab.tab());
                for (int navStart = 0; navStart < navPlan.windowCount(); navStart++) {
                    for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                        String frameId = tabFrameId(flatTab.tab().id(), navStart, pageIndex);
                        frames.put(frameId, new MenuFrame(title, buildTabPage(flatTab.tab(), pageIndex, totalPages, utilities, flatTabs,
                                navPlan, navStart, sharedFooter)));
                    }
                }
            }

            String initialFrameId = tabFrameId(initialTabId, initialNavStart, 0);
            Menu menu = new StandardMenu(title, MenuGeometry.TABS, LIST_ROWS, initialFrameId, frames);
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
            Map<String, MenuFrame> frames = Map.of("canvas:0", new MenuFrame(title, buildCanvasPage(rows, placed, utilities)));
            Menu menu = new StandardMenu(title, MenuGeometry.CANVAS, rows, "canvas:0", frames);
            MenuValidator.validate(menu);
            return menu;
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
        int firstItem = pageIndex * LIST_CONTENT_SIZE;
        int lastItem = Math.min(items.size(), firstItem + LIST_CONTENT_SIZE);
        int slotIndex = 0;
        for (int i = firstItem; i < lastItem; i++) {
            int slot = LIST_CONTENT_SLOTS.get(slotIndex++);
            slots.put(slot, HouseMenuCompiler.compile(slot, items.get(i)));
        }
        applyUtilities(slots, footerStart, utilities);
        applySharedFooter(slots, footerStart,
                pageIndex > 0 ? listFrameId(pageIndex - 1) : null,
                pageIndex + 1 < totalPages ? listFrameId(pageIndex + 1) : null);
        return orderedSlots(slots, LIST_ROWS);
    }

    private static void clearListContentArea(Map<Integer, MenuSlot> slots) {
        for (int slot : LIST_CONTENT_SLOTS) {
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

    private static void renderTabChrome(Map<Integer, MenuSlot> slots, MenuTab activeTab, List<FlatTab> flatTabs, NavPlan navPlan,
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
        int firstItem = pageIndex * TABS_LIST_CONTENT_SIZE;
        int lastItem = Math.min(items.size(), firstItem + TABS_LIST_CONTENT_SIZE);
        int slotIndex = 0;
        for (int i = firstItem; i < lastItem; i++) {
            int slot = TABS_LIST_CONTENT_SLOTS.get(slotIndex++);
            slots.put(slot, HouseMenuCompiler.compile(slot, items.get(i)));
        }
    }

    private static void clearTabListContentArea(Map<Integer, MenuSlot> slots) {
        for (int slot : TABS_LIST_CONTENT_SLOTS) {
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

    private static void applySharedFooter(
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
        return chromeButton(slot, plain(tab.name()), tab.icon(),
                Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.SWITCH_TAB, new MenuSlotAction.OpenFrame(tabFrameId(tab.id(), navStart, 0)))),
                active);
    }

    private static MenuSlot tabIndicator(int slot, boolean active) {
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

    private static MenuSlot filler(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("black_stained_glass_pane"), Component.text(" "),
                List.of(), false, Map.of());
    }

    private static MenuSlot empty(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("air"), Component.empty(), List.of(), false, Map.of());
    }

    private static MenuSlot simpleButton(int slot, String title, NamedTextColor color, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions) {
        return new MenuSlot(slot, icon,
                Component.text(title, color).decoration(TextDecoration.ITALIC, false),
                List.of(),
                false,
                interactions);
    }

    private static MenuSlot navigationButton(int slot, String title, int pageNumber, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions) {
        return new MenuSlot(slot, icon,
                Component.text(title, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Page " + pageNumber, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)),
                false,
                interactions);
    }

    private static MenuSlot chromeButton(int slot, String title, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions) {
        return chromeButton(slot, title, icon, interactions, false);
    }

    private static MenuSlot chromeButton(int slot, String title, MenuIcon icon, Map<MenuClick, MenuInteraction> interactions, boolean glow) {
        return new MenuSlot(slot, icon, Component.text(title).decoration(TextDecoration.ITALIC, false),
                chromeLore(title, interactions), glow, interactions);
    }

    private static List<Component> chromeLore(String title, Map<MenuClick, MenuInteraction> interactions) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(title, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (!interactions.isEmpty()) {
            lore.add(Component.empty());
            MenuInteraction left = interactions.get(MenuClick.LEFT);
            if (left != null) {
                lore.add(promptLine(interactions.size() == 1 ? "CLICK" : "LEFT CLICK", left.promptLabel()));
            }
            MenuInteraction right = interactions.get(MenuClick.RIGHT);
            if (right != null) {
                lore.add(promptLine("RIGHT CLICK", right.promptLabel()));
            }
        }
        return List.copyOf(lore);
    }

    private static Component promptLine(String clickLabel, String promptLabel) {
        return Component.text()
                .append(Component.text(clickLabel, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" to " + emphaticPromptLabel(promptLabel), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static String emphaticPromptLabel(String promptLabel) {
        return promptLabel.endsWith("!") ? promptLabel : promptLabel + "!";
    }

    private static void applyUtilities(Map<Integer, MenuSlot> slots, int footerStart, Map<UtilitySlot, MenuItem> utilities) {
        for (Map.Entry<UtilitySlot, MenuItem> entry : utilities.entrySet()) {
            int slot = entry.getKey().resolveSlot(footerStart);
            slots.put(slot, HouseMenuCompiler.compile(slot, entry.getValue()));
        }
    }

    private static Map<Integer, MenuSlot> createFilledSlots(int rows) {
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

    private static List<MenuSlot> orderedSlots(Map<Integer, MenuSlot> slots, int rows) {
        List<MenuSlot> ordered = new ArrayList<>();
        for (int slot = 0; slot < rows * 9; slot++) {
            ordered.add(slots.get(slot));
        }
        return List.copyOf(ordered);
    }

    private static List<FlatTab> flattenTabs(List<MenuTabGroup> groups) {
        List<FlatTab> flattened = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            MenuTabGroup group = groups.get(groupIndex);
            for (MenuTab tab : group.tabs()) {
                flattened.add(new FlatTab(tab, groupIndex));
            }
        }
        return List.copyOf(flattened);
    }

    private static NavPlan buildNavPlan(List<FlatTab> flatTabs) {
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

    private static List<PositionedTab> positionTabs(List<FlatTab> flatTabs, NavWindow window, boolean overflow) {
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

    private static int contentPageCount(MenuTab tab) {
        if (tab.content() instanceof MenuTabContent.ListContent list) {
            return Math.max(1, (list.items().size() + TABS_LIST_CONTENT_SIZE - 1) / TABS_LIST_CONTENT_SIZE);
        }
        return 1;
    }

    private static String listFrameId(int pageIndex) {
        return "page:" + pageIndex;
    }

    private static String tabFrameId(String tabId, int navStart, int pageIndex) {
        return "tab:" + tabId + ":nav:" + navStart + ":page:" + pageIndex;
    }

    private static Component listFrameTitle(Component title, int pageIndex, int totalPages) {
        if (totalPages <= 1) {
            return title;
        }
        return Component.text().append(title).append(Component.text(" (" + (pageIndex + 1) + "/" + totalPages + ")")).build();
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

    private static void validateUtilitySlots(Map<UtilitySlot, MenuItem> utilities, int footerStart, Set<Integer> reserved) {
        for (UtilitySlot slot : utilities.keySet()) {
            int resolved = slot.resolveSlot(footerStart);
            if (reserved.contains(resolved)) {
                throw new IllegalArgumentException("Utility slot " + slot + " collides with reserved house chrome");
            }
        }
    }

    private static Set<Integer> reservedSharedFooterSlots(int footerStart, boolean hasPrevious, boolean hasNext) {
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

    private static Set<Integer> reservedCanvasFooterSlots(int footerStart) {
        Set<Integer> reserved = new HashSet<>();
        reserved.add(footerStart + FOOTER_BACK_OFFSET);
        reserved.add(footerStart + FOOTER_CLOSE_OFFSET);
        return reserved;
    }

    private record FlatTab(MenuTab tab, int groupIndex) {
    }

    private record NavWindow(int startIndex, int endIndex, int renderedWidth) {
    }

    private record NavPlan(boolean overflow, List<NavWindow> windows) {

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

    private record PositionedTab(FlatTab flatTab, int slot) {
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
}
