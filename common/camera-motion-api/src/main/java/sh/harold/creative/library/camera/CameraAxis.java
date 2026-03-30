package sh.harold.creative.library.camera;

import java.util.Objects;

public record CameraAxis(double amplitudeDegrees, long periodTicks, long phaseTicks, Waveform waveform, long seed) {

    public CameraAxis {
        if (!Double.isFinite(amplitudeDegrees)) {
            throw new IllegalArgumentException("amplitudeDegrees must be finite");
        }
        if (periodTicks <= 0L) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        Objects.requireNonNull(waveform, "waveform");
    }
}
