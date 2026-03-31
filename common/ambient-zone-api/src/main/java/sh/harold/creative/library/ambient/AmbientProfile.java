package sh.harold.creative.library.ambient;

public record AmbientProfile(
        Double overlayStrength,
        Double particleStrength,
        Double soundStrength,
        Double cameraStrength,
        Double borderPressure
) {

    public AmbientProfile {
        validate(overlayStrength, "overlayStrength");
        validate(particleStrength, "particleStrength");
        validate(soundStrength, "soundStrength");
        validate(cameraStrength, "cameraStrength");
        validate(borderPressure, "borderPressure");
    }

    public static AmbientProfile empty() {
        return new AmbientProfile(null, null, null, null, null);
    }

    public boolean emptyProfile() {
        return overlayStrength == null
                && particleStrength == null
                && soundStrength == null
                && cameraStrength == null
                && borderPressure == null;
    }

    private static void validate(Double value, String name) {
        if (value != null && (!Double.isFinite(value) || value < 0.0)) {
            throw new IllegalArgumentException(name + " must be null or a finite non-negative value");
        }
    }
}
