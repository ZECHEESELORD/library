package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuDefinition;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.UtilitySlot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MenuSessionState {

    private static final int FOOTER_BACK_OFFSET = 3;
    private static final int FOOTER_CLOSE_OFFSET = 4;

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private final AtomicLong revision = new AtomicLong();
    private volatile SessionEntry current;
    private volatile boolean autoBackEligible;
    private volatile MenuSessionView cachedView;

    public MenuSessionState(MenuDefinition menu) {
        open(menu);
    }

    public MenuDefinition menu() {
        return current.menu();
    }

    public String frameId() {
        return current.frameId();
    }

    public long revision() {
        return revision.get();
    }

    public Map<String, Object> values() {
        return values;
    }

    public boolean reactive() {
        return current instanceof ReactiveEntry;
    }

    public long tickIntervalTicks() {
        if (current instanceof ReactiveEntry reactive) {
            return reactive.menu().tickIntervalTicks();
        }
        return 0L;
    }

    public MenuFrame currentFrame() {
        return currentView().frame();
    }

    MenuSessionView currentView() {
        MenuSessionView view = cachedView;
        if (view != null) {
            return view;
        }
        view = buildCurrentView();
        cachedView = view;
        return view;
    }

    public Optional<MenuSlot> slot(int slot) {
        return currentView().slot(slot);
    }

    public Optional<MenuInteraction> interaction(int slot, MenuClick click) {
        return slot(slot).map(menuSlot -> menuSlot.interactions().get(click));
    }

    public MenuStack cursor() {
        return currentView().cursor();
    }

    public void open(MenuDefinition menu) {
        history.clear();
        this.current = newEntry(menu);
        this.autoBackEligible = false;
        invalidate();
    }

    public void openChild(MenuDefinition menu) {
        pushCurrent();
        this.current = newEntry(menu);
        this.autoBackEligible = true;
        invalidate();
    }

    public void openFrame(String frameId) {
        Objects.requireNonNull(frameId, "frameId");
        if (!(current instanceof CompiledEntry compiled)) {
            throw new IllegalStateException("Only compiled menus support frame navigation");
        }
        compiled.menu().frame(frameId);
        if (frameId.equals(compiled.frameId())) {
            return;
        }
        pushCurrent();
        this.current = new CompiledEntry(compiled.menu(), frameId);
        invalidate();
    }

    public boolean back() {
        HistoryEntry previous = history.pollFirst();
        if (previous == null) {
            return false;
        }
        this.current = previous.entry();
        this.autoBackEligible = previous.autoBackEligible();
        invalidate();
        return true;
    }

    public List<ReactiveMenuEffect> opened() {
        return dispatchLifecycle(new ReactiveMenuInput.Opened());
    }

    public void closed() {
        dispatchLifecycle(new ReactiveMenuInput.Closed());
    }

    public List<ReactiveMenuEffect> tick() {
        if (!(current instanceof ReactiveEntry reactive)) {
            return List.of();
        }
        return dispatchLifecycle(new ReactiveMenuInput.Tick(reactive.tick() + 1L));
    }

    public List<ReactiveMenuEffect> dispatchReactive(ReactiveMenuInput input) {
        Objects.requireNonNull(input, "input");
        if (!(current instanceof ReactiveEntry)) {
            return List.of();
        }
        return dispatchLifecycle(input);
    }

    private List<ReactiveMenuEffect> dispatchLifecycle(ReactiveMenuInput input) {
        if (!(current instanceof ReactiveEntry reactive)) {
            return List.of();
        }
        ReactiveMenuResult<?> result = reactive.menu().reduce(reactive.state(), input);
        long nextTick = input instanceof ReactiveMenuInput.Tick tick ? tick.tick() : reactive.tick();
        this.current = new ReactiveEntry(reactive.menu(), result.state(), nextTick);
        invalidate();
        return result.effects();
    }

    private void pushCurrent() {
        history.addFirst(new HistoryEntry(current, autoBackEligible, historyTitle(current)));
    }

    private SessionEntry newEntry(MenuDefinition menu) {
        Objects.requireNonNull(menu, "menu");
        if (menu instanceof Menu compiled) {
            return new CompiledEntry(compiled, compiled.initialFrameId());
        }
        if (menu instanceof ReactiveMenuDefinition reactive) {
            return new ReactiveEntry(reactive, reactive.createState(), 0L);
        }
        if (menu instanceof ReactiveMenu) {
            throw new IllegalArgumentException("Reactive menus must be built by the shared menu service");
        }
        throw new IllegalArgumentException("Unsupported menu definition: " + menu.getClass().getName());
    }

    private MenuSessionView buildCurrentView() {
        MenuSessionView view;
        if (current instanceof CompiledEntry compiled) {
            MenuFrame frame = compiled.menu().frame(compiled.frameId());
            view = new MenuSessionView(frame.title(), frame.slots(), null);
        } else if (current instanceof ReactiveEntry reactive) {
            view = buildReactiveView(reactive.menu(), reactive.state());
        } else {
            throw new IllegalStateException("Unsupported session entry: " + current);
        }
        if (!autoBackEligible || history.isEmpty()) {
            return view;
        }
        return overlayBack(view, history.peekFirst().titleSnapshot());
    }

    private MenuSessionView buildReactiveView(ReactiveMenuDefinition menu, Object state) {
        ReactiveMenuView rendered = menu.render(state);
        int rows = menu.rows();
        int size = rows * 9;
        boolean fillWithBlackPane = rendered.fillWithBlackPane() != null ? rendered.fillWithBlackPane() : menu.fillWithBlackPane();
        Map<Integer, MenuSlot> slots = new LinkedHashMap<>();
        for (int slot = 0; slot < size; slot++) {
            slots.put(slot, fillWithBlackPane ? filler(slot) : empty(slot));
        }
        for (Map.Entry<Integer, MenuItem> entry : rendered.placements().entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("Reactive view slot " + slot + " is outside a " + rows + "-row menu");
            }
            slots.put(slot, HouseMenuCompiler.compile(slot, entry.getValue()));
        }
        int footerStart = HouseMenuCompiler.footerStart(rows);
        applyUtilities(slots, footerStart, menu.utilities(), size);
        int closeSlot = footerStart + FOOTER_CLOSE_OFFSET;
        if (closeSlot < size) {
            slots.put(closeSlot, closeButton(closeSlot));
        }
        return new MenuSessionView(rendered.title(), orderedSlots(slots, size), rendered.cursor());
    }

    private void invalidate() {
        cachedView = null;
        revision.incrementAndGet();
    }

    private static MenuSessionView overlayBack(MenuSessionView view, Component previousTitle) {
        int backSlot = HouseMenuCompiler.footerStart(view.slots().size() / 9) + FOOTER_BACK_OFFSET;
        if (backSlot >= view.slots().size()) {
            return view;
        }
        List<MenuSlot> slots = new ArrayList<>(view.slots());
        slots.set(backSlot, backButton(backSlot, previousTitle));
        return new MenuSessionView(view.title(), slots, view.cursor());
    }

    private static void applyUtilities(Map<Integer, MenuSlot> slots, int footerStart, Map<UtilitySlot, MenuItem> utilities, int size) {
        for (Map.Entry<UtilitySlot, MenuItem> entry : utilities.entrySet()) {
            int slot = entry.getKey().resolveSlot(footerStart);
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("Reactive utility slot " + entry.getKey() + " resolves outside the menu");
            }
            slots.put(slot, HouseMenuCompiler.compile(slot, entry.getValue()));
        }
    }

    private static List<MenuSlot> orderedSlots(Map<Integer, MenuSlot> slots, int size) {
        List<MenuSlot> ordered = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            ordered.add(slots.get(slot));
        }
        return List.copyOf(ordered);
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

    private static MenuSlot backButton(int slot, Component previousMenuTitle) {
        String previousTitle = ComponentText.flatten(previousMenuTitle);
        return new MenuSlot(
                slot,
                MenuIcon.vanilla("arrow"),
                Component.text("Go Back", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("To " + previousTitle, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                false,
                Map.of(MenuClick.LEFT, MenuInteraction.of(ActionVerb.BACK, new MenuSlotAction.Execute(MenuContext::back))));
    }

    private Component historyTitle(SessionEntry entry) {
        if (entry instanceof CompiledEntry compiled) {
            return compiled.menu().title();
        }
        return currentView().title();
    }

    private sealed interface SessionEntry permits CompiledEntry, ReactiveEntry {

        MenuDefinition menu();

        String frameId();
    }

    private record CompiledEntry(Menu menu, String frameId) implements SessionEntry {
    }

    private record ReactiveEntry(ReactiveMenuDefinition menu, Object state, long tick) implements SessionEntry {

        @Override
        public String frameId() {
            return "";
        }
    }

    private record HistoryEntry(SessionEntry entry, boolean autoBackEligible, Component titleSnapshot) {
    }
}
