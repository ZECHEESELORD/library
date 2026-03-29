package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.CanvasMenuBuilder;
import sh.harold.creative.library.menu.ListMenuBuilder;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuService;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.TabsMenuBuilder;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class StandardMenuService implements MenuService {

    private static final int LIST_ROWS = 6;
    private static final int LIST_CONTENT_START = 9;
    private static final int LIST_CONTENT_END = 44;
    private static final int LIST_CONTENT_SIZE = LIST_CONTENT_END - LIST_CONTENT_START + 1;
    private static final int TABS_CONTENT_START = 18;
    private static final int TABS_CONTENT_END = 44;
    private static final int TABS_CONTENT_SIZE = TABS_CONTENT_END - TABS_CONTENT_START + 1;
    private static final int FOOTER_PREVIOUS_OR_BACK_OFFSET = 0;
    private static final int FOOTER_SECONDARY_LEFT_OFFSET = 1;
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
        private sh.harold.creative.library.menu.MenuAction backAction;

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
        public ListMenuBuilder back(sh.harold.creative.library.menu.MenuAction action) {
            this.backAction = Objects.requireNonNull(action, "action");
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
                frames.put(frameId(pageIndex), new MenuFrame(title, buildListPage(pageIndex, totalPages, items, utilities, backAction)));
            }
            Menu menu = new StandardMenu(MenuGeometry.LIST, LIST_ROWS, frameId(0), frames);
            MenuValidator.validate(menu);
            return menu;
        }

        private static String frameId(int pageIndex) {
            return "page:" + pageIndex;
        }
    }

    private static final class DefaultTabsMenuBuilder implements TabsMenuBuilder {

        private Component title = Component.text("Menu");
        private final List<MenuTab> tabs = new ArrayList<>();
        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();
        private sh.harold.creative.library.menu.MenuAction backAction;
        private String defaultTabId;

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
        public TabsMenuBuilder back(sh.harold.creative.library.menu.MenuAction action) {
            this.backAction = Objects.requireNonNull(action, "action");
            return this;
        }

        @Override
        public TabsMenuBuilder utility(UtilitySlot slot, MenuItem item) {
            utilities.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(item, "item"));
            return this;
        }

        @Override
        public TabsMenuBuilder defaultTab(String tabId) {
            this.defaultTabId = Objects.requireNonNull(tabId, "tabId");
            return this;
        }

        @Override
        public TabsMenuBuilder addTab(MenuTab tab) {
            tabs.add(Objects.requireNonNull(tab, "tab"));
            return this;
        }

        @Override
        public Menu build() {
            if (tabs.size() < 2) {
                throw new IllegalStateException("Tabs menu requires at least two tabs");
            }
            if (tabs.size() > 9) {
                throw new IllegalStateException("v1 tabs currently supports up to 9 tabs");
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (MenuTab tab : tabs) {
                if (!ids.add(tab.id())) {
                    throw new IllegalStateException("Duplicate tab id: " + tab.id());
                }
            }
            if (defaultTabId != null && tabs.stream().noneMatch(tab -> tab.id().equals(defaultTabId))) {
                throw new IllegalStateException("Default tab id does not exist: " + defaultTabId);
            }
            Map<String, MenuFrame> frames = new LinkedHashMap<>();
            String initialFrameId = null;
            for (MenuTab tab : tabs) {
                List<MenuItem> tabItems = tab.items();
                int totalPages = Math.max(1, (tabItems.size() + TABS_CONTENT_SIZE - 1) / TABS_CONTENT_SIZE);
                for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                    String frameId = frameId(tab.id(), pageIndex);
                    if (initialFrameId == null && (defaultTabId == null || defaultTabId.equals(tab.id())) && pageIndex == 0) {
                        initialFrameId = frameId;
                    }
                    frames.put(frameId, new MenuFrame(title, buildTabPage(tab, pageIndex, totalPages, utilities, backAction, tabs)));
                }
            }
            if (initialFrameId == null) {
                initialFrameId = frameId(tabs.get(0).id(), 0);
            }
            Menu menu = new StandardMenu(MenuGeometry.TABS, LIST_ROWS, initialFrameId, frames);
            MenuValidator.validate(menu);
            return menu;
        }

        private static String frameId(String tabId, int pageIndex) {
            return "tab:" + tabId + ":page:" + pageIndex;
        }
    }

    private static final class DefaultCanvasMenuBuilder implements CanvasMenuBuilder {

        private Component title = Component.text("Menu");
        private final Map<Integer, MenuItem> placed = new LinkedHashMap<>();
        private final Map<UtilitySlot, MenuItem> utilities = new LinkedHashMap<>();
        private sh.harold.creative.library.menu.MenuAction backAction;
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
        public CanvasMenuBuilder back(sh.harold.creative.library.menu.MenuAction action) {
            this.backAction = Objects.requireNonNull(action, "action");
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
            Map<String, MenuFrame> frames = Map.of("canvas:0", new MenuFrame(title, buildCanvasPage(rows, placed, utilities, backAction)));
            Menu menu = new StandardMenu(MenuGeometry.CANVAS, rows, "canvas:0", frames);
            MenuValidator.validate(menu);
            return menu;
        }
    }

    private static List<MenuSlot> buildListPage(
            int pageIndex,
            int totalPages,
            List<MenuItem> items,
            Map<UtilitySlot, MenuItem> utilities,
            sh.harold.creative.library.menu.MenuAction backAction
    ) {
        Map<Integer, MenuSlot> slots = createFilledSlots(LIST_ROWS);
        int footerStart = HouseMenuCompiler.footerStart(LIST_ROWS);
        validateUtilitySlots(utilities, footerStart,
                reservedFooterSlots(footerStart, totalPages > 1 && pageIndex > 0, totalPages > 1 && pageIndex + 1 < totalPages, backAction != null));
        int firstItem = pageIndex * LIST_CONTENT_SIZE;
        int lastItem = Math.min(items.size(), firstItem + LIST_CONTENT_SIZE);
        int contentSlot = LIST_CONTENT_START;
        for (int i = firstItem; i < lastItem; i++) {
            slots.put(contentSlot, HouseMenuCompiler.compile(contentSlot, items.get(i)));
            contentSlot++;
        }
        applyUtilities(slots, footerStart, utilities);
        if (totalPages > 1 && pageIndex > 0) {
            slots.put(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET,
                    chromeButton(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET, "Previous Page", MenuIcon.vanilla("arrow"),
                    MenuInteraction.of(ActionVerb.PREVIOUS_PAGE, new MenuSlotAction.OpenFrame("page:" + (pageIndex - 1)))));
        } else if (backAction != null) {
            slots.put(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET,
                    backButton(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET, backAction));
        }
        if (totalPages > 1 && pageIndex > 0 && backAction != null) {
            int backSlot = footerStart + FOOTER_SECONDARY_LEFT_OFFSET;
            slots.put(backSlot, backButton(backSlot, backAction));
        }
        if (totalPages > 1 && pageIndex + 1 < totalPages) {
            slots.put(footerStart + FOOTER_NEXT_OFFSET,
                    chromeButton(footerStart + FOOTER_NEXT_OFFSET, "Next Page", MenuIcon.vanilla("arrow"),
                    MenuInteraction.of(ActionVerb.NEXT_PAGE, new MenuSlotAction.OpenFrame("page:" + (pageIndex + 1)))));
        }
        slots.put(footerStart + FOOTER_CLOSE_OFFSET,
                chromeButton(footerStart + FOOTER_CLOSE_OFFSET, "Close", MenuIcon.vanilla("barrier"),
                MenuInteraction.of(ActionVerb.CLOSE, new MenuSlotAction.Close())));
        return orderedSlots(slots, LIST_ROWS);
    }

    private static List<MenuSlot> buildTabPage(
            MenuTab activeTab,
            int pageIndex,
            int totalPages,
            Map<UtilitySlot, MenuItem> utilities,
            sh.harold.creative.library.menu.MenuAction backAction,
            List<MenuTab> tabs
    ) {
        Map<Integer, MenuSlot> slots = createFilledSlots(LIST_ROWS);
        int footerStart = HouseMenuCompiler.footerStart(LIST_ROWS);
        for (int i = 0; i < tabs.size(); i++) {
            MenuTab tab = tabs.get(i);
            String targetFrame = "tab:" + tab.id() + ":page:0";
            MenuSlot slot = chromeButton(
                    i,
                    tab.equals(activeTab) ? "» " + plain(tab.name()) : plain(tab.name()),
                    tab.icon(),
                    MenuInteraction.of(ActionVerb.SWITCH_TAB, new MenuSlotAction.OpenFrame(targetFrame)),
                    tab.equals(activeTab));
            slots.put(i, slot);
        }
        validateUtilitySlots(utilities, footerStart,
                reservedFooterSlots(footerStart, totalPages > 1 && pageIndex > 0, totalPages > 1 && pageIndex + 1 < totalPages, backAction != null));
        int firstItem = pageIndex * TABS_CONTENT_SIZE;
        int lastItem = Math.min(activeTab.items().size(), firstItem + TABS_CONTENT_SIZE);
        int contentSlot = TABS_CONTENT_START;
        for (int i = firstItem; i < lastItem; i++) {
            slots.put(contentSlot, HouseMenuCompiler.compile(contentSlot, activeTab.items().get(i)));
            contentSlot++;
        }
        applyUtilities(slots, footerStart, utilities);
        if (totalPages > 1 && pageIndex > 0) {
            slots.put(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET,
                    chromeButton(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET, "Previous Page", MenuIcon.vanilla("arrow"),
                    MenuInteraction.of(ActionVerb.PREVIOUS_PAGE, new MenuSlotAction.OpenFrame("tab:" + activeTab.id() + ":page:" + (pageIndex - 1)))));
        } else if (backAction != null) {
            slots.put(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET,
                    backButton(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET, backAction));
        }
        if (totalPages > 1 && pageIndex > 0 && backAction != null) {
            slots.put(footerStart + FOOTER_SECONDARY_LEFT_OFFSET,
                    backButton(footerStart + FOOTER_SECONDARY_LEFT_OFFSET, backAction));
        }
        if (totalPages > 1 && pageIndex + 1 < totalPages) {
            slots.put(footerStart + FOOTER_NEXT_OFFSET,
                    chromeButton(footerStart + FOOTER_NEXT_OFFSET, "Next Page", MenuIcon.vanilla("arrow"),
                    MenuInteraction.of(ActionVerb.NEXT_PAGE, new MenuSlotAction.OpenFrame("tab:" + activeTab.id() + ":page:" + (pageIndex + 1)))));
        }
        slots.put(footerStart + FOOTER_CLOSE_OFFSET,
                chromeButton(footerStart + FOOTER_CLOSE_OFFSET, "Close", MenuIcon.vanilla("barrier"),
                MenuInteraction.of(ActionVerb.CLOSE, new MenuSlotAction.Close())));
        return orderedSlots(slots, LIST_ROWS);
    }

    private static List<MenuSlot> buildCanvasPage(
            int rows,
            Map<Integer, MenuItem> placed,
            Map<UtilitySlot, MenuItem> utilities,
            sh.harold.creative.library.menu.MenuAction backAction
    ) {
        Map<Integer, MenuSlot> slots = createFilledSlots(rows);
        int footerStart = HouseMenuCompiler.footerStart(rows);
        validateUtilitySlots(utilities, footerStart, reservedFooterSlots(footerStart, false, false, backAction != null));
        for (UtilitySlot slot : utilities.keySet()) {
            int reserved = slot.resolveSlot(footerStart);
            if (placed.containsKey(reserved)) {
                throw new IllegalArgumentException("Placed item collides with utility chrome slot " + reserved);
            }
        }
        for (Map.Entry<Integer, MenuItem> entry : placed.entrySet()) {
            if (entry.getKey() >= footerStart && (entry.getKey() == footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET
                    || entry.getKey() == footerStart + FOOTER_CLOSE_OFFSET
                    || entry.getKey() == footerStart + FOOTER_NEXT_OFFSET)) {
                throw new IllegalArgumentException("Placed items may not overwrite reserved canvas chrome slots");
            }
            slots.put(entry.getKey(), HouseMenuCompiler.compile(entry.getKey(), entry.getValue()));
        }
        applyUtilities(slots, footerStart, utilities);
        if (backAction != null) {
            slots.put(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET,
                    backButton(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET, backAction));
        }
        slots.put(footerStart + FOOTER_CLOSE_OFFSET,
                chromeButton(footerStart + FOOTER_CLOSE_OFFSET, "Close", MenuIcon.vanilla("barrier"),
                MenuInteraction.of(ActionVerb.CLOSE, new MenuSlotAction.Close())));
        return orderedSlots(slots, rows);
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

    private static MenuSlot filler(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("black_stained_glass_pane"), Component.text(" "),
                List.of(), false, Map.of());
    }

    private static MenuSlot backButton(int slot, sh.harold.creative.library.menu.MenuAction action) {
        return chromeButton(slot, "Back", MenuIcon.vanilla("arrow"),
                new MenuInteraction(ActionVerb.BACK, ActionVerb.BACK.promptLabel(), new MenuSlotAction.Execute(action)));
    }

    private static MenuSlot chromeButton(int slot, String title, MenuIcon icon, MenuInteraction interaction) {
        return chromeButton(slot, title, icon, interaction, false);
    }

    private static MenuSlot chromeButton(int slot, String title, MenuIcon icon, MenuInteraction interaction, boolean glow) {
        return new MenuSlot(slot, icon, Component.text(title).decoration(TextDecoration.ITALIC, false), chromeLore(title, interaction), glow,
                Map.of(sh.harold.creative.library.menu.MenuClick.LEFT, interaction));
    }

    private static List<Component> chromeLore(String title, MenuInteraction interaction) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(title, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text()
                .append(Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" to " + interaction.promptLabel(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false)
                .build());
        return List.copyOf(lore);
    }

    private static List<MenuSlot> orderedSlots(Map<Integer, MenuSlot> slots, int rows) {
        List<MenuSlot> ordered = new ArrayList<>();
        for (int slot = 0; slot < rows * 9; slot++) {
            ordered.add(slots.get(slot));
        }
        return List.copyOf(ordered);
    }

    private static String plain(Component component) {
        return ComponentText.flatten(component);
    }

    private static void validateUtilitySlots(Map<UtilitySlot, MenuItem> utilities, int footerStart, java.util.Set<Integer> reserved) {
        for (UtilitySlot slot : utilities.keySet()) {
            int resolved = slot.resolveSlot(footerStart);
            if (reserved.contains(resolved)) {
                throw new IllegalArgumentException("Utility slot " + slot + " collides with reserved house chrome");
            }
        }
    }

    private static java.util.Set<Integer> reservedFooterSlots(int footerStart, boolean hasPrevious, boolean hasNext, boolean hasBack) {
        java.util.Set<Integer> reserved = new java.util.HashSet<>();
        if (hasPrevious || hasBack) {
            reserved.add(footerStart + FOOTER_PREVIOUS_OR_BACK_OFFSET);
        }
        if (hasPrevious && hasBack) {
            reserved.add(footerStart + FOOTER_SECONDARY_LEFT_OFFSET);
        }
        if (hasNext) {
            reserved.add(footerStart + FOOTER_NEXT_OFFSET);
        }
        reserved.add(footerStart + FOOTER_CLOSE_OFFSET);
        return reserved;
    }
}
