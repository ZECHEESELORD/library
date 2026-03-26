package sh.harold.creative.library.message.core;

import sh.harold.creative.library.message.MessageValue;
import sh.harold.creative.library.message.SlotBinding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MessageBindings {

    private MessageBindings() {
    }

    public static Map<String, MessageValue> fromSlots(SlotBinding... slots) {
        Objects.requireNonNull(slots, "slots");
        LinkedHashMap<String, MessageValue> bindings = new LinkedHashMap<>();
        for (SlotBinding slot : slots) {
            Objects.requireNonNull(slot, "slot");
            MessageValue previous = bindings.put(slot.name(), slot.value());
            if (previous != null) {
                throw new IllegalArgumentException("duplicate slot binding: " + slot.name());
            }
        }
        return Collections.unmodifiableMap(bindings);
    }

    public static Map<String, MessageValue> copy(Map<String, MessageValue> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        LinkedHashMap<String, MessageValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, MessageValue> entry : bindings.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "bindings key");
            Objects.requireNonNull(entry.getValue(), "bindings value");
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    public static Map<String, MessageValue> with(Map<String, MessageValue> bindings, String name, MessageValue value) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        Objects.requireNonNull(value, "value");
        LinkedHashMap<String, MessageValue> copy = new LinkedHashMap<>(bindings);
        copy.put(name, value);
        return Collections.unmodifiableMap(copy);
    }
}
