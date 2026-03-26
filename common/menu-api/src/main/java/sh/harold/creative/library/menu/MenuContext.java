package sh.harold.creative.library.menu;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuContext {

    public interface SessionControls {

        SessionControls NOOP = new SessionControls() {
            @Override
            public void refresh() {
            }

            @Override
            public void open(Menu menu) {
            }

            @Override
            public void close() {
            }
        };

        void refresh();

        void open(Menu menu);

        void close();
    }

    private final MenuClick click;
    private final String frameId;
    private final Map<String, Object> values;
    private final SessionControls controls;

    public MenuContext() {
        this(MenuClick.LEFT, "");
    }

    public MenuContext(MenuClick click, String frameId) {
        this(click, frameId, new ConcurrentHashMap<>(), SessionControls.NOOP);
    }

    public MenuContext(MenuClick click, String frameId, Map<String, Object> values, SessionControls controls) {
        this.click = java.util.Objects.requireNonNull(click, "click");
        this.frameId = java.util.Objects.requireNonNull(frameId, "frameId");
        this.values = java.util.Objects.requireNonNull(values, "values");
        this.controls = java.util.Objects.requireNonNull(controls, "controls");
    }

    public MenuClick click() {
        return click;
    }

    public String frameId() {
        return frameId;
    }

    public void refresh() {
        controls.refresh();
    }

    public void open(Menu menu) {
        controls.open(java.util.Objects.requireNonNull(menu, "menu"));
    }

    public void close() {
        controls.close();
    }

    public void set(String key, Object value) {
        values.put(key, value);
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = values.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }
}
