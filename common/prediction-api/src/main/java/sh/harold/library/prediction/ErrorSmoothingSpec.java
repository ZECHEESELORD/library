package sh.harold.library.prediction;

import sh.harold.library.tween.spring.SpringSpec;

import java.util.Objects;

/**
 * How a correction's visual error closes (architecture section 5, "Visual error smoothing"). The simulated
 * state jumps to the corrected value; the drawn value keeps the old offset and springs it to zero. Errors larger
 * than {@code snapDistance} snap instead (a teleport is honest when the error is that large).
 */
public record ErrorSmoothingSpec(SpringSpec spring, double snapDistance) {

    /** Critically damped at 9 Hz: about 98% closed in 0.1 s (6 frames at 60 fps); snaps beyond 8 units. */
    public static final ErrorSmoothingSpec DEFAULT = new ErrorSmoothingSpec(SpringSpec.critical(9.0), 8.0);

    public ErrorSmoothingSpec {
        Objects.requireNonNull(spring, "spring");
        if (!(snapDistance > 0.0)) {
            throw new IllegalArgumentException("snapDistance must be positive");
        }
    }
}
