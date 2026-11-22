package sh.harold.library.prediction.core;

import sh.harold.library.prediction.ErrorSmoothingSpec;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tween.core.Vec3Spring;

import java.util.Objects;

/**
 * Visual error smoothing for a corrected position (architecture section 5). The simulation jumps to the
 * corrected value at once; the drawn value is {@code simulated + offset}, where a correction adds
 * {@code previous - corrected} to the offset and a critically damped spring closes it over a few frames. A small
 * correction is invisible, a large one reads as a stumble, and one past the snap distance snaps.
 *
 * <p>Steps once per tick and samples per frame like every stateful sampler (catalog R3). Not thread-safe.
 */
public final class ErrorSmoother {

    private final ErrorSmoothingSpec spec;
    private final Vec3Spring offset;

    public ErrorSmoother(ErrorSmoothingSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.offset = new Vec3Spring(spec.spring(), Vec3.ZERO);
    }

    public ErrorSmoothingSpec spec() {
        return spec;
    }

    /**
     * Records a correction that moved the simulated value from {@code previous} to {@code corrected}.
     *
     * @return true if the error was absorbed, false if it was past the snap distance and snapped
     */
    public boolean absorb(Vec3 previous, Vec3 corrected) {
        Vec3 total = offset.position().add(previous.subtract(corrected));
        if (total.length() > spec.snapDistance()) {
            offset.reset(Vec3.ZERO);
            return false;
        }
        offset.kickDisplacement(previous.subtract(corrected));
        return true;
    }

    public void tick() {
        offset.tick();
    }

    /** The remaining offset {@code partial} ticks after the last tick. */
    public Vec3 offset(double partial) {
        return offset.sample(partial);
    }

    /** Where to draw: {@code simulated + offset(partial)}. */
    public Vec3 drawn(Vec3 simulated, double partial) {
        return simulated.add(offset(partial));
    }

    /** Drops any remaining offset (a cut, a teleport, a respawn). */
    public void clear() {
        offset.reset(Vec3.ZERO);
    }
}
