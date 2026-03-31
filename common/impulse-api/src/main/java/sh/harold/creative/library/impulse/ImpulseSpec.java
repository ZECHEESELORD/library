package sh.harold.creative.library.impulse;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tween.Envelope;

import java.util.Objects;

public record ImpulseSpec(
        Key key,
        ImpulseMode mode,
        ImpulseVector vector,
        long delayTicks,
        long durationTicks,
        Envelope envelope,
        AxisMask axisMask,
        ImpulseStackMode stackMode,
        double maxMagnitude,
        int priority,
        InstanceConflictPolicy conflictPolicy
) {

    public ImpulseSpec {
        key = Objects.requireNonNull(key, "key");
        mode = Objects.requireNonNull(mode, "mode");
        vector = Objects.requireNonNull(vector, "vector");
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        if (durationTicks < 0L) {
            throw new IllegalArgumentException("durationTicks cannot be negative");
        }
        envelope = Objects.requireNonNull(envelope, "envelope");
        axisMask = Objects.requireNonNull(axisMask, "axisMask");
        stackMode = Objects.requireNonNull(stackMode, "stackMode");
        if (!Double.isFinite(maxMagnitude) || maxMagnitude < 0.0) {
            throw new IllegalArgumentException("maxMagnitude must be finite and non-negative");
        }
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
    }
}
