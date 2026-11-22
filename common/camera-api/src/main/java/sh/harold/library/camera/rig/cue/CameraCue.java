package sh.harold.library.camera.rig.cue;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.tick.InstanceConflictPolicy;
import sh.harold.library.tween.Easing;
import sh.harold.library.tween.EasingFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A timed set of channel curves started together against the rig's additive layers (stage 5). Cues sum: two live cues
 * on the same channel add (look scale multiplies).
 *
 * @param key           the cue's slot. Starting a cue whose key is already live applies {@code conflict}
 * @param owner         who started it (a feature or system), for {@code cancelAll(owner)} and telemetry
 * @param curves        the curves, at least one
 * @param releaseTicks  how long a released curve takes to fade to zero, for curves without their own release
 * @param releaseEasing the fraction faded over {@code releaseTicks}
 * @param conflict      {@code REPLACE} (default) closes the live cue and starts this one; {@code REFRESH} restarts the
 *                      live cue's handle with this spec; {@code REJECT} keeps the live cue and returns its handle
 */
public record CameraCue(
        Key key,
        Key owner,
        List<ChannelCurve> curves,
        double releaseTicks,
        EasingFunction releaseEasing,
        InstanceConflictPolicy conflict
) {

    public CameraCue {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(owner, "owner");
        curves = List.copyOf(Objects.requireNonNull(curves, "curves"));
        if (curves.isEmpty()) {
            throw new IllegalArgumentException("a cue needs at least one curve");
        }
        if (!Double.isFinite(releaseTicks) || releaseTicks < 0.0) {
            throw new IllegalArgumentException("releaseTicks must be finite and non-negative");
        }
        Objects.requireNonNull(releaseEasing, "releaseEasing");
        Objects.requireNonNull(conflict, "conflict");
    }

    /** Whether a curve starts from the current eye ({@link ChannelCurve#absoluteEyeHeightFrom}). */
    public boolean startsFromEye() {
        return curves.stream().anyMatch(curve -> curve.fromEye().isPresent());
    }

    /** This cue with every curve that starts from the current eye built from {@code eye}; itself when there are none. */
    public CameraCue startingFrom(double eye) {
        if (!startsFromEye() || !Double.isFinite(eye)) {
            return this;
        }
        return new CameraCue(key, owner, curves.stream().map(curve -> curve.startingFrom(eye)).toList(),
                releaseTicks, releaseEasing, conflict);
    }

    public static Builder builder(Key key) {
        return new Builder(key);
    }

    public static final class Builder {

        private final Key key;
        private Key owner;
        private final List<ChannelCurve> curves = new ArrayList<>();
        private double releaseTicks;
        private EasingFunction releaseEasing = Easing.QUAD_OUT;
        private InstanceConflictPolicy conflict = InstanceConflictPolicy.REPLACE;

        private Builder(Key key) {
            this.key = Objects.requireNonNull(key, "key");
            this.owner = key;
        }

        public Builder owner(Key owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
            return this;
        }

        public Builder curve(ChannelCurve curve) {
            curves.add(Objects.requireNonNull(curve, "curve"));
            return this;
        }

        public Builder channel(CameraChannel channel, CueCurve curve) {
            return curve(ChannelCurve.of(channel, curve));
        }

        /** Scales look input to {@code factor} (0.2 for 20%) for {@code ticks}. */
        public Builder lookScale(double factor, double ticks) {
            if (!(factor >= 0.0) || !Double.isFinite(factor)) {
                throw new IllegalArgumentException("factor must be finite and non-negative");
            }
            return channel(CameraChannel.LOOK_SCALE, CueCurve.constant(factor - 1.0, ticks));
        }

        public Builder release(double ticks, EasingFunction easing) {
            this.releaseTicks = ticks;
            this.releaseEasing = Objects.requireNonNull(easing, "easing");
            return this;
        }

        public Builder conflict(InstanceConflictPolicy policy) {
            this.conflict = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public CameraCue build() {
            return new CameraCue(key, owner, curves, releaseTicks, releaseEasing, conflict);
        }
    }
}
