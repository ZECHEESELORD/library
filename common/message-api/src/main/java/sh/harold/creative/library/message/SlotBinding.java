package sh.harold.creative.library.message;

import java.util.Objects;

public record SlotBinding(String name, MessageValue value) {

    public SlotBinding {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        Objects.requireNonNull(value, "value");
    }
}
