package sh.harold.library.npc.behavior;

public record NpcPersonalityTuning(
        double radiusMultiplier,
        double tempoMultiplier,
        double intensityMultiplier
) {
    public static final NpcPersonalityTuning DEFAULT = new NpcPersonalityTuning(1.0, 1.0, 1.0);

    public NpcPersonalityTuning {
        requireRange(radiusMultiplier, 0.5, 2.0, "radiusMultiplier");
        requireRange(tempoMultiplier, 0.5, 2.0, "tempoMultiplier");
        requireRange(intensityMultiplier, 0.0, 2.0, "intensityMultiplier");
    }

    private static void requireRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and in [" + minimum + ", " + maximum + "]");
        }
    }
}
