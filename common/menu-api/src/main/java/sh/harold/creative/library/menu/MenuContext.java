package sh.harold.creative.library.menu;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuContext {

    private final MenuClick click;
    private final String frameId;
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public MenuContext() {
        this(MenuClick.LEFT, "");
    }

    public MenuContext(MenuClick click, String frameId) {
        this.click = java.util.Objects.requireNonNull(click, "click");
        this.frameId = java.util.Objects.requireNonNull(frameId, "frameId");
    }

    public MenuClick click() {
        return click;
    }

    public String frameId() {
        return frameId;
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
