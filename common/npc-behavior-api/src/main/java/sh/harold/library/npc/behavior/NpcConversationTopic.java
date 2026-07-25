package sh.harold.library.npc.behavior;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record NpcConversationTopic(
        Key key,
        List<Component> lines,
        List<Component> interruptionLines
) {
    public NpcConversationTopic {
        Objects.requireNonNull(key, "key");
        lines = copyNonNull(lines, "lines");
        interruptionLines = copyNonNull(interruptionLines, "interruptionLines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lines cannot be empty");
        }
    }

    public NpcConversationTopic(Key key, Collection<? extends Component> lines) {
        this(key, List.copyOf(lines), List.of());
    }

    public static NpcConversationTopic of(Key key, Collection<? extends Component> lines) {
        return new NpcConversationTopic(key, List.copyOf(lines), List.of());
    }

    public static NpcConversationTopic of(
            Key key,
            Collection<? extends Component> lines,
            Collection<? extends Component> interruptionLines
    ) {
        return new NpcConversationTopic(key, List.copyOf(lines), List.copyOf(interruptionLines));
    }

    private static <T> List<T> copyNonNull(List<? extends T> source, String name) {
        Objects.requireNonNull(source, name);
        List<T> copy = List.copyOf(source);
        for (T value : copy) {
            Objects.requireNonNull(value, name + " contains null");
        }
        return copy;
    }
}
