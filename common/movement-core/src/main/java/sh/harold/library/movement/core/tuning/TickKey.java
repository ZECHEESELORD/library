package sh.harold.library.movement.core.tuning;

/**
 * One key of a tuned camera or arm curve, in move ticks: {@code value} at the end of move tick {@code T<tick>}, which
 * is cue-local time {@code tick + 1}. Written in tuning files as a pair, {@code [3, 15]} for "15 at T3".
 */
public record TickKey(int tick, double value) {

    /** The cue-local time of this key. */
    public double localTime() {
        return tick + 1.0;
    }
}
