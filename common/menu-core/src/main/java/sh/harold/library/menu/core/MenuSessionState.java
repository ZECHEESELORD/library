package sh.harold.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuInteraction;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.MenuSlotAction;
import sh.harold.library.menu.ReactiveMenu;
import sh.harold.library.menu.ReactiveMenuEffect;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveMenuResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MenuSessionState {

    private static final int FOOTER_BACK_OFFSET = 3;
    private static final int MAX_HISTORY_DEPTH = 32;

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private final ReactivePlacementCache reactivePlacementCache = new ReactivePlacementCache();
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

    public Map<String, Integer> custodyTargets() {
        if (current instanceof ReactiveEntry reactive) {
            return reactive.menu().custodyTargets();
        }
        return Map.of();
    }

    public Optional<String> custodyTargetAt(int slot) {
        return custodyTargets().entrySet().stream()
                .filter(entry -> entry.getValue() == slot)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public MenuCustodyDecision decideCustody(MenuCustodyGesture gesture, MenuCustodySnapshot snapshot) {
        Objects.requireNonNull(gesture, "gesture");
        Objects.requireNonNull(snapshot, "snapshot");
        if (current instanceof ReactiveEntry reactive) {
            return reactive.menu().decideCustody(reactive.state(), gesture, snapshot);
        }
        return MenuCustodyDecision.reject();
    }

    public void invalidateView() {
        invalidate();
    }

    public void open(MenuDefinition menu) {
        prepareOpen(menu).commit();
    }

    public void openChild(MenuDefinition menu) {
        prepareOpenChild(menu).ifPresent(PreparedTransition::commit);
    }

    public void replaceCurrent(MenuDefinition menu) {
        prepareReplaceCurrent(menu).commit();
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
        prepareOpenFrame(frameId).ifPresent(PreparedTransition::commit);
    }

    public boolean back() {
        Optional<PreparedTransition> transition = prepareBack();
        transition.ifPresent(PreparedTransition::commit);
        return transition.isPresent();
    }

    public PreparedTransition prepareOpen(MenuDefinition menu) {
        return new PreparedTransition(
                newEntry(menu),
                new ArrayDeque<>(),
                false);
    }

    public Optional<PreparedTransition> prepareOpenChild(MenuDefinition menu) {
        Objects.requireNonNull(menu, "menu");
        if (history.size() >= MAX_HISTORY_DEPTH) {
            MenuTrace.field("navigationRejected", "historyDepth");
            return Optional.empty();
        }
        Deque<HistoryEntry> nextHistory = new ArrayDeque<>(history);
        nextHistory.addFirst(new HistoryEntry(current, autoBackEligible, historyTitle(current)));
        return Optional.of(new PreparedTransition(newEntry(menu), nextHistory, true));
    }

    public PreparedTransition prepareReplaceCurrent(MenuDefinition menu) {
        return new PreparedTransition(newEntry(menu), new ArrayDeque<>(history), autoBackEligible);
    }

    public Optional<PreparedTransition> prepareOpenFrame(String frameId) {
        Objects.requireNonNull(frameId, "frameId");
        if (!(current instanceof CompiledEntry compiled)) {
            throw new IllegalStateException("Only compiled menus support frame navigation");
        }
        compiled.menu().frame(frameId);
        if (frameId.equals(compiled.frameId())) {
            return Optional.empty();
        }
        return Optional.of(new PreparedTransition(
                new CompiledEntry(compiled.menu(), frameId),
                new ArrayDeque<>(history),
                autoBackEligible));
    }

    public Optional<PreparedTransition> prepareBack() {
        HistoryEntry previous = history.peekFirst();
        if (previous == null) {
            return Optional.empty();
        }
        Deque<HistoryEntry> nextHistory = new ArrayDeque<>(history);
        nextHistory.removeFirst();
        return Optional.of(new PreparedTransition(previous.entry(), nextHistory, previous.autoBackEligible()));
    }

    public List<ReactiveMenuEffect> opened() {
        return MenuTrace.time("state.opened", () -> dispatchLifecycle(new ReactiveMenuInput.Opened()));
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
        return MenuTrace.time("state.dispatchReactive", () -> prepareReactive(input).commit());
    }

    public PreparedReactiveDispatch prepareReactive(ReactiveMenuInput input) {
        Objects.requireNonNull(input, "input");
        if (!(current instanceof ReactiveEntry reactive)) {
            throw new IllegalStateException("The current menu is not reactive");
        }
        return prepareLifecycle(reactive, input);
    }

    private List<ReactiveMenuEffect> dispatchLifecycle(ReactiveMenuInput input) {
        if (!(current instanceof ReactiveEntry reactive)) {
            return List.of();
        }
        return prepareLifecycle(reactive, input).commit();
    }

    private PreparedReactiveDispatch prepareLifecycle(ReactiveEntry reactive, ReactiveMenuInput input) {
        ReactiveMenuResult<?> result = reactive.menu().reduce(reactive.state(), input);
        long nextTick = input instanceof ReactiveMenuInput.Tick tick ? tick.tick() : reactive.tick();
        ReactiveEntry nextEntry = result.stateChanged()
                ? new ReactiveEntry(reactive.menu(), result.state(), nextTick)
                : new ReactiveEntry(reactive.menu(), reactive.state(), nextTick);
        MenuSessionView nextView = result.stateChanged()
                ? buildView(nextEntry, autoBackEligible, history)
                : cachedView;
        return new PreparedReactiveDispatch(
                reactive,
                nextEntry,
                nextView,
                result.effect().stream().toList(),
                result.stateChanged());
    }

    private List<ReactiveMenuEffect> commitPreparedReactive(
            ReactiveEntry expected,
            ReactiveEntry nextEntry,
            MenuSessionView nextView,
            List<ReactiveMenuEffect> effects,
            boolean stateChanged
    ) {
        if (current != expected) {
            throw new IllegalStateException("Reactive dispatch is stale");
        }
        current = nextEntry;
        if (stateChanged) {
            cachedView = nextView;
            revision.incrementAndGet();
        }
        return effects;
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
        return buildView(current, autoBackEligible, history);
    }

    private MenuSessionView buildView(SessionEntry entry, boolean backEligible, Deque<HistoryEntry> entryHistory) {
        MenuSessionView view;
        if (entry instanceof CompiledEntry compiled) {
            MenuFrame frame = compiled.menu().frame(compiled.frameId());
            MenuTrace.title(frame.title());
            view = new MenuSessionView(frame.title(), frame.slots());
        } else if (entry instanceof ReactiveEntry reactive) {
            view = buildReactiveView(reactive.menu(), reactive.state());
        } else {
            throw new IllegalStateException("Unsupported session entry: " + entry);
        }
        if (!backEligible || entryHistory.isEmpty()) {
            return view;
        }
        return MenuTrace.time("state.overlayBack", () -> overlayBack(view, entryHistory.peekFirst().titleSnapshot()));
    }

    private MenuSessionView buildReactiveView(ReactiveMenuDefinition menu, Object state) {
        reactivePlacementCache.beginRender();
        MenuSessionView view = menu.buildView(state, reactivePlacementCache);
        MenuTrace.setCount("placementCompileHits", reactivePlacementCache.hits());
        MenuTrace.setCount("placementCompileMisses", reactivePlacementCache.misses());
        return view;
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
        return new MenuSessionView(view.title(), slots, view.reactiveClickTargets());
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

    public final class PreparedTransition {

        private final long expectedRevision = revision.get();
        private final SessionEntry expectedEntry = current;
        private final SessionEntry nextEntry;
        private final Deque<HistoryEntry> nextHistory;
        private final boolean nextAutoBackEligible;
        private MenuSessionView preparedView;
        private boolean committed;

        private PreparedTransition(
                SessionEntry nextEntry,
                Deque<HistoryEntry> nextHistory,
                boolean nextAutoBackEligible
        ) {
            this.nextEntry = Objects.requireNonNull(nextEntry, "nextEntry");
            this.nextHistory = new ArrayDeque<>(nextHistory);
            this.nextAutoBackEligible = nextAutoBackEligible;
        }

        public MenuDefinition menu() {
            return nextEntry.menu();
        }

        public String frameId() {
            return nextEntry.frameId();
        }

        public boolean reactive() {
            return nextEntry instanceof ReactiveEntry;
        }

        public long tickIntervalTicks() {
            if (nextEntry instanceof ReactiveEntry reactive) {
                return reactive.menu().tickIntervalTicks();
            }
            return 0L;
        }

        public MenuFrame currentFrame() {
            return view().frame();
        }

        public Map<String, Integer> custodyTargets() {
            if (nextEntry instanceof ReactiveEntry reactive) {
                return reactive.menu().custodyTargets();
            }
            return Map.of();
        }

        public void commit() {
            if (committed) {
                throw new IllegalStateException("Transition is already committed");
            }
            if (revision.get() != expectedRevision || current != expectedEntry) {
                throw new IllegalStateException("Transition is stale");
            }
            MenuSessionView nextView = view();
            current = nextEntry;
            history.clear();
            history.addAll(nextHistory);
            autoBackEligible = nextAutoBackEligible;
            cachedView = nextView;
            revision.incrementAndGet();
            committed = true;
        }

        private MenuSessionView view() {
            if (preparedView == null) {
                preparedView = buildView(nextEntry, nextAutoBackEligible, nextHistory);
            }
            return preparedView;
        }
    }

    public final class PreparedReactiveDispatch {

        private final ReactiveEntry expectedEntry;
        private final ReactiveEntry nextEntry;
        private final MenuSessionView nextView;
        private final List<ReactiveMenuEffect> effects;
        private final boolean stateChanged;
        private boolean committed;

        private PreparedReactiveDispatch(
                ReactiveEntry expectedEntry,
                ReactiveEntry nextEntry,
                MenuSessionView nextView,
                List<ReactiveMenuEffect> effects,
                boolean stateChanged
        ) {
            this.expectedEntry = expectedEntry;
            this.nextEntry = nextEntry;
            this.nextView = nextView;
            this.effects = List.copyOf(effects);
            this.stateChanged = stateChanged;
        }

        public boolean stateChanged() {
            return stateChanged;
        }

        public MenuFrame currentFrame() {
            return nextView == null ? MenuSessionState.this.currentFrame() : nextView.frame();
        }

        public List<ReactiveMenuEffect> effects() {
            return effects;
        }

        public List<ReactiveMenuEffect> commit() {
            if (committed) {
                throw new IllegalStateException("Reactive dispatch is already committed");
            }
            List<ReactiveMenuEffect> committedEffects = commitPreparedReactive(
                    expectedEntry, nextEntry, nextView, effects, stateChanged);
            committed = true;
            return committedEffects;
        }
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
