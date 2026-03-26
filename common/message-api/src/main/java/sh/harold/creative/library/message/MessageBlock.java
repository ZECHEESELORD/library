package sh.harold.creative.library.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.TextColor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface MessageBlock {

    List<Entry> entries();

    void send(Audience audience);

    sealed interface Entry permits BlankEntry, TitleEntry, LineEntry, BulletEntry {
    }

    record BlankEntry() implements Entry {
    }

    record TitleEntry(String text, TextColor color) implements Entry {

        public TitleEntry {
            Objects.requireNonNull(text, "text");
            if (text.isBlank()) {
                throw new IllegalArgumentException("text cannot be blank");
            }
            Objects.requireNonNull(color, "color");
        }
    }

    record LineEntry(String template, Map<String, MessageValue> bindings) implements Entry {

        public LineEntry {
            Objects.requireNonNull(template, "template");
            if (template.isBlank()) {
                throw new IllegalArgumentException("template cannot be blank");
            }
            bindings = immutableBindings(bindings);
        }
    }

    record BulletEntry(String template, Map<String, MessageValue> bindings) implements Entry {

        public BulletEntry {
            Objects.requireNonNull(template, "template");
            if (template.isBlank()) {
                throw new IllegalArgumentException("template cannot be blank");
            }
            bindings = immutableBindings(bindings);
        }
    }

    private static Map<String, MessageValue> immutableBindings(Map<String, MessageValue> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        LinkedHashMap<String, MessageValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, MessageValue> entry : bindings.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "bindings key");
            Objects.requireNonNull(entry.getValue(), "bindings value");
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
