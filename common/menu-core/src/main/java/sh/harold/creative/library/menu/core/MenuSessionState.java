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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MenuSessionState {

    private static final int FOOTER_BACK_OFFSET = 3;

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private final Map<ReactivePlacementKey, MenuSlot> reactivePlacementCache = new HashMap<>();
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
        return MenuTrace.time("state.currentFrame", () -> currentView().frame());
    }

    MenuSessionView currentView() {
        MenuSessionView view = cachedView;
        if (view != null) {
            return view;
        }
        view = MenuTrace.time("state.currentView", this::buildCurrentView);
        cachedView = view;
        return view;
    }

    public Optional<MenuSlot> slot(int slot) {
        return currentView().slot(slot);
    }

    public Optional<MenuInteraction> interaction(int slot, MenuClick click) {
        return slot(slot).map(menuSlot -> menuSlot.interactions().get(click));
    }

    public boolean acceptsReactiveClick(int slot) {
        return currentView().acceptsReactiveClick(slot);
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
        return MenuTrace.time("state.opened", () -> dispatchLifecycle(new ReactiveMenuInput.Opened()));
    }

    public void closed() {
        MenuTrace.time("state.closed", () -> {
            dispatchLifecycle(new ReactiveMenuInput.Closed());
        });
    }

    public List<ReactiveMenuEffect> tick() {
        if (!(current instanceof ReactiveEntry reactive)) {
            return List.of();
        }
        return MenuTrace.time("state.tick", () -> dispatchLifecycle(new ReactiveMenuInput.Tick(reactive.tick() + 1L)));
    }

    public List<ReactiveMenuEffect> dispatchReactive(ReactiveMenuInput input) {
        Objects.requireNonNull(input, "input");
        if (!(current instanceof ReactiveEntry)) {
            return List.of();
        }
        return MenuTrace.time("state.dispatchReactive", () -> dispatchLifecycle(input));
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
            MenuTrace.title(frame.title());
            view = new MenuSessionView(frame.title(), frame.slots(), null);
        } else if (current instanceof ReactiveEntry reactive) {
            view = buildReactiveView(reactive.menu(), reactive.state());
        } else {
            throw new IllegalStateException("Unsupported session entry: " + current);
        }
        if (!autoBackEligible || history.isEmpty()) {
            return view;
        }
        return MenuTrace.time("state.overlayBack", () -> overlayBack(view, history.peekFirst().titleSnapshot()));
    }

    private MenuSessionView buildReactiveView(ReactiveMenuDefinition menu, Object state) {
        ReactiveMenuView rendered = MenuTrace.time("state.reactive.render", () -> menu.render(state));
        MenuTrace.title(rendered.title());
        MenuTrace.setCount("placementCount", rendered.placements().size());
        boolean fillWithBlackPane = rendered.fillWithBlackPane() != null ? rendered.fillWithBlackPane() : menu.fillWithBlackPane();
        List<MenuSlot> baseSlots = MenuTrace.time("state.reactive.baseSlots", () -> menu.baseSlots(fillWithBlackPane));
        int size = baseSlots.size();
        int placementCompileHits = 0;
        int placementCompileMisses = 0;
        if (rendered.placements().isEmpty()) {
            MenuTrace.setCount("placementCompileHits", placementCompileHits);
            MenuTrace.setCount("placementCompileMisses", placementCompileMisses);
            return new MenuSessionView(rendered.title(), baseSlots, rendered.cursor());
        }
        List<MenuSlot> slots = new ArrayList<>(baseSlots);
        Set<Integer> reactiveClickTargets = new HashSet<>();
        for (Map.Entry<Integer, MenuItem> entry : rendered.placements().entrySet()) {
            int slot = entry.getKey();
            MenuItem item = entry.getValue();
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("Reactive view slot " + slot + " is outside a " + menu.rows() + "-row menu");
            }
            ReactivePlacementKey cacheKey = new ReactivePlacementKey(slot, item);
            MenuSlot compiled = reactivePlacementCache.get(cacheKey);
            if (compiled != null) {
                placementCompileHits++;
            } else {
                placementCompileMisses++;
                long started = System.nanoTime();
                compiled = HouseMenuCompiler.compile(slot, item);
                long elapsed = System.nanoTime() - started;
                reactivePlacementCache.put(cacheKey, compiled);
                MenuTrace.addDuration("state.reactive.compilePlacements", elapsed);
                MenuSlot compiledSlot = compiled;
                MenuTrace.detailIfSlow("placement-compile", elapsed,
                        () -> "slot=" + slot + " title=" + ComponentText.flatten(compiledSlot.title()));
            }
            slots.set(slot, compiled);
            reactiveClickTargets.add(slot);
        }
        MenuTrace.setCount("placementCompileHits", placementCompileHits);
        MenuTrace.setCount("placementCompileMisses", placementCompileMisses);
        return new MenuSessionView(rendered.title(), List.copyOf(slots), rendered.cursor(), reactiveClickTargets);
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
        return new MenuSessionView(view.title(), slots, view.cursor(), view.reactiveClickTargets());
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

    private static final class ReactivePlacementKey {

        private final int slot;
        private final MenuItem item;

        private ReactivePlacementKey(int slot, MenuItem item) {
            this.slot = slot;
            this.item = Objects.requireNonNull(item, "item");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ReactivePlacementKey key)) {
                return false;
            }
            return slot == key.slot && item == key.item;
        }

        @Override
        public int hashCode() {
            return 31 * slot + System.identityHashCode(item);
        }
    }
}
