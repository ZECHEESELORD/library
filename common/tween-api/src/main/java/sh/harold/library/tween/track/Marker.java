package sh.harold.library.tween.track;

import java.util.Objects;

/**
 * A named event on a track. Events fire on whole ticks only (catalog R1), so a marker sits on a tick.
 * During playback a marker fires when clock time crosses its tick going forward. On a seek past it, it fires
 * once only if {@code sticky} (sky state, hides); otherwise it is skipped.
 */
public record Marker(long tick, String name, String payload, boolean sticky) {

    public Marker {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(payload, "payload");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }

    public static Marker of(long tick, String name) {
        return new Marker(tick, name, "", false);
    }

    public static Marker sticky(long tick, String name) {
        return new Marker(tick, name, "", true);
    }
}
