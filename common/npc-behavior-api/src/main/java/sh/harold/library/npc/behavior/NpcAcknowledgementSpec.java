package sh.harold.library.npc.behavior;

import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record NpcAcknowledgementSpec(
        List<NpcGesturePreset> gestures,
        List<Component> barkLines
) {
    public NpcAcknowledgementSpec {
        gestures = copyNonNull(gestures, "gestures");
        barkLines = copyNonNull(barkLines, "barkLines");
        if (gestures.isEmpty() && barkLines.isEmpty()) {
            throw new IllegalArgumentException("An acknowledgement needs at least one gesture or bark line");
        }
    }

    public static NpcAcknowledgementSpec gestures(NpcGesturePreset first, NpcGesturePreset... additional) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(additional, "additional");
        List<NpcGesturePreset> gestures = new java.util.ArrayList<>(1 + additional.length);
        gestures.add(first);
        for (NpcGesturePreset gesture : additional) {
            gestures.add(Objects.requireNonNull(gesture, "additional contains null"));
        }
        return new NpcAcknowledgementSpec(gestures, List.of());
    }

    public static NpcAcknowledgementSpec of(
            Collection<NpcGesturePreset> gestures,
            Collection<? extends Component> barkLines
    ) {
        return new NpcAcknowledgementSpec(List.copyOf(gestures), List.copyOf(barkLines));
    }

    private static <T> List<T> copyNonNull(List<? extends T> values, String name) {
        Objects.requireNonNull(values, name);
        List<T> copy = List.copyOf(values);
        for (T value : copy) {
            Objects.requireNonNull(value, name + " contains null");
        }
        return copy;
    }
}
