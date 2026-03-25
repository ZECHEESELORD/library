package sh.harold.creative.library.statemachine;

import java.util.Objects;

public record TimerKey(String value) {

    public TimerKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }
}
