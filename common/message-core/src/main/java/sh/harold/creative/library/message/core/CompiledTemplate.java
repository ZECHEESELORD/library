package sh.harold.creative.library.message.core;

import sh.harold.creative.library.message.MessageValue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CompiledTemplate {

    private final String template;
    private final List<Segment> segments;
    private final Set<String> referencedSlots;
    private final Set<String> clickSlots;

    private CompiledTemplate(String template, List<Segment> segments, Set<String> referencedSlots, Set<String> clickSlots) {
        this.template = template;
        this.segments = List.copyOf(segments);
        this.referencedSlots = Set.copyOf(referencedSlots);
        this.clickSlots = Set.copyOf(clickSlots);
    }

    static CompiledTemplate parse(String template) {
        Objects.requireNonNull(template, "template");
        if (template.isBlank()) {
            throw new IllegalArgumentException("template cannot be blank");
        }

        List<Segment> segments = new ArrayList<>();
        LinkedHashSet<String> referencedSlots = new LinkedHashSet<>();
        LinkedHashSet<String> clickSlots = new LinkedHashSet<>();
        StringBuilder text = new StringBuilder();

        for (int index = 0; index < template.length(); index++) {
            char character = template.charAt(index);
            if (character == '}') {
                throw new IllegalArgumentException("template contains an unmatched closing brace: " + template);
            }
            if (character != '{') {
                text.append(character);
                continue;
            }

            if (!text.isEmpty()) {
                segments.add(new Segment.Text(text.toString()));
                text.setLength(0);
            }

            int end = template.indexOf('}', index + 1);
            if (end < 0) {
                throw new IllegalArgumentException("template contains an unmatched opening brace: " + template);
            }

            String placeholder = template.substring(index + 1, end);
            if (placeholder.isBlank() || placeholder.indexOf('{') >= 0) {
                throw new IllegalArgumentException("template contains an invalid placeholder: {" + placeholder + "}");
            }

            if (placeholder.startsWith("click:")) {
                String slotName = placeholder.substring("click:".length());
                requireSlotName(slotName);
                segments.add(new Segment.ClickPrompt(slotName));
                referencedSlots.add(slotName);
                clickSlots.add(slotName);
            } else {
                requireSlotName(placeholder);
                segments.add(new Segment.Slot(placeholder));
                referencedSlots.add(placeholder);
            }

            index = end;
        }

        if (!text.isEmpty()) {
            segments.add(new Segment.Text(text.toString()));
        }

        return new CompiledTemplate(template, segments, referencedSlots, clickSlots);
    }

    String template() {
        return template;
    }

    List<Segment> segments() {
        return segments;
    }

    void validate(Map<String, MessageValue> bindings) {
        Objects.requireNonNull(bindings, "bindings");

        LinkedHashSet<String> missing = new LinkedHashSet<>(referencedSlots);
        missing.removeAll(bindings.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("missing slot bindings " + missing + " for template: " + template);
        }

        LinkedHashSet<String> unknown = new LinkedHashSet<>(bindings.keySet());
        unknown.removeAll(referencedSlots);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown slot bindings " + unknown + " for template: " + template);
        }

        for (String clickSlot : clickSlots) {
            if (bindings.get(clickSlot).clickAction().isEmpty()) {
                throw new IllegalArgumentException("{click:" + clickSlot + "} requires a click-enabled slot");
            }
        }
    }

    private static void requireSlotName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("slot name cannot be blank");
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && character != '-') {
                throw new IllegalArgumentException("slot names may only contain letters, numbers, '_' or '-': " + name);
            }
        }
    }

    sealed interface Segment permits Segment.Text, Segment.Slot, Segment.ClickPrompt {

        record Text(String value) implements Segment {

            public Text {
                Objects.requireNonNull(value, "value");
            }
        }

        record Slot(String name) implements Segment {

            public Slot {
                Objects.requireNonNull(name, "name");
            }
        }

        record ClickPrompt(String name) implements Segment {

            public ClickPrompt {
                Objects.requireNonNull(name, "name");
            }
        }
    }
}
