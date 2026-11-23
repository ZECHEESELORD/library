package sh.harold.library.movement.core.tuning;

/**
 * One key of a tuned first-person arm curve: {@code pose} at the end of move tick {@code T<tick>} (cue-local time
 * {@code tick + 1}). Written in tuning files as {@code [tick, x, y, z, rotX, rotY, rotZ]}.
 */
public record ArmKey(int tick, ArmPoseTuning pose) {

    /** The cue-local time of this key. */
    public double localTime() {
        return tick + 1.0;
    }
}
