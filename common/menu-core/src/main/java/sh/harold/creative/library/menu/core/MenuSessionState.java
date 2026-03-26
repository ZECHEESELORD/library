package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuSessionState {

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private volatile Menu menu;
    private volatile String frameId;

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
        return frame(frameId);
    }

    public MenuFrame frame(String frameId) {
        MenuFrame frame = menu.frames().get(frameId);
        if (frame == null) {
            throw new IllegalArgumentException("Unknown menu frame: " + frameId);
        }
        return frame;
    }

    public void open(Menu menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.frameId = menu.initialFrameId();
    }

    public void openFrame(String frameId) {
        frame(frameId);
        this.frameId = frameId;
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
}
