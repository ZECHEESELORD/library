package sh.harold.creative.library.tween;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.tick.InstanceConflictPolicy;

import java.util.Objects;

public record Tween<T>(
        Key key,
        T from,
        T to,
        Interpolator<T> interpolator,
        long delayTicks,
        long durationTicks,
        Easing easing,
        Envelope envelope,
        HoldBehavior holdBehavior,
        RepeatSpec repeat,
        InstanceConflictPolicy conflictPolicy
) {

    public Tween {
        key = Objects.requireNonNull(key, "key");
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        interpolator = Objects.requireNonNull(interpolator, "interpolator");
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        if (durationTicks < 0L) {
            throw new IllegalArgumentException("durationTicks cannot be negative");
        }
        easing = Objects.requireNonNull(easing, "easing");
        envelope = Objects.requireNonNull(envelope, "envelope");
        holdBehavior = Objects.requireNonNull(holdBehavior, "holdBehavior");
        repeat = Objects.requireNonNull(repeat, "repeat");
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
    }
}
