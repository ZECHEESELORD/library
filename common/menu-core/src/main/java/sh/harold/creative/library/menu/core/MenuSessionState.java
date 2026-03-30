package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuSessionState {

    private static final int FOOTER_BACK_SLOT = 48;

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private volatile Menu menu;
    private volatile String frameId;
    private volatile boolean autoBackEligible;

    public MenuSessionState(Menu menu) {
        open(menu);
    }

    public Menu menu() {
        return menu;
    }

    public String frameId() {
        return frameId;
    }

    public Map<String, Object> values() {
        return values;
    }

    public MenuFrame currentFrame() {
        MenuFrame frame = frame(frameId);
        if (!autoBackEligible || history.isEmpty()) {
            return frame;
        }
        return overlayBack(frame, history.peekFirst().menu().title());
    }

    public MenuFrame frame(String frameId) {
        MenuFrame frame = menu.frames().get(frameId);
        if (frame == null) {
            throw new IllegalArgumentException("Unknown menu frame: " + frameId);
        }
        return frame;
    }

    public void open(Menu menu) {
        history.clear();
        this.menu = Objects.requireNonNull(menu, "menu");
        this.frameId = menu.initialFrameId();
        this.autoBackEligible = false;
    }

    public void openChild(Menu menu) {
        Objects.requireNonNull(menu, "menu");
        pushCurrent();
        this.menu = menu;
        this.frameId = menu.initialFrameId();
        this.autoBackEligible = true;
    }

    public void openFrame(String frameId) {
        frame(frameId);
        if (frameId.equals(this.frameId)) {
            return;
        }
        pushCurrent();
        this.frameId = frameId;
    }

    public boolean back() {
        HistoryEntry previous = history.pollFirst();
        if (previous == null) {
            return false;
        }
        this.menu = previous.menu();
        this.frameId = previous.frameId();
        this.autoBackEligible = previous.autoBackEligible();
        return true;
    }

    public Optional<MenuSlot> slot(int slot) {
        MenuFrame frame = currentFrame();
        if (slot < 0 || slot >= frame.slots().size()) {
            return Optional.empty();
        }
        return Optional.of(frame.slots().get(slot));
    }

    public Optional<MenuInteraction> interaction(int slot, MenuClick click) {
        return slot(slot).map(menuSlot -> menuSlot.interactions().get(click));
    }

    private void pushCurrent() {
        history.addFirst(new HistoryEntry(menu, frameId, autoBackEligible));
    }

    private static MenuFrame overlayBack(MenuFrame frame, Component previousMenuTitle) {
        if (FOOTER_BACK_SLOT >= frame.slots().size()) {
            return frame;
        }
        List<MenuSlot> slots = new ArrayList<>(frame.slots());
        slots.set(FOOTER_BACK_SLOT, backButton(FOOTER_BACK_SLOT, previousMenuTitle));
        return new MenuFrame(frame.title(), slots);
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

    private record HistoryEntry(Menu menu, String frameId, boolean autoBackEligible) {
    }
}
