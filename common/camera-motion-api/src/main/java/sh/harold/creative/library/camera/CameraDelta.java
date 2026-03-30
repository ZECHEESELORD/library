package sh.harold.creative.library.camera;

public record CameraDelta(double yawDegrees, double pitchDegrees) {

    private static final CameraDelta NONE = new CameraDelta(0.0, 0.0);

    public CameraDelta {
        validate(yawDegrees, "yawDegrees");
        validate(pitchDegrees, "pitchDegrees");
    }

    public static CameraDelta none() {
        return NONE;
    }

    public CameraDelta plus(CameraDelta other) {
        return plus(other.yawDegrees, other.pitchDegrees);
    }

    public CameraDelta plus(double yawDelta, double pitchDelta) {
        validate(yawDelta, "yawDelta");
        validate(pitchDelta, "pitchDelta");
        if (yawDelta == 0.0 && pitchDelta == 0.0) {
            return this;
        }
        return new CameraDelta(yawDegrees + yawDelta, pitchDegrees + pitchDelta);
    }

    public boolean isZero() {
        return yawDegrees == 0.0 && pitchDegrees == 0.0;
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
