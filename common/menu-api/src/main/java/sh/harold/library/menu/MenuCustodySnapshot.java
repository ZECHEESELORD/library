package sh.harold.library.menu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The committed custody state visible to a policy or reducer.
 *
 * <p>The native stacks remain private to the platform adapter.</p>
 */
public record MenuCustodySnapshot(Map<String, MenuCustodyItem> targets, Optional<MenuCustodyItem> cursor) {

    public static final MenuCustodySnapshot EMPTY = new MenuCustodySnapshot(Map.of(), Optional.empty());

    public MenuCustodySnapshot {
        Objects.requireNonNull(targets, "targets");
        Map<String, MenuCustodyItem> copy = new LinkedHashMap<>();
        targets.forEach((key, item) -> {
            Objects.requireNonNull(key, "target key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("target key cannot be blank");
            }
            copy.put(key, Objects.requireNonNull(item, "target item"));
        });
        targets = Collections.unmodifiableMap(copy);
        cursor = Objects.requireNonNull(cursor, "cursor");
    }

    public boolean empty() {
        return targets.isEmpty() && cursor.isEmpty();
    }
}
