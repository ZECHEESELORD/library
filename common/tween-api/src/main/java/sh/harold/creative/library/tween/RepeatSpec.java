package sh.harold.creative.library.tween;

public record RepeatSpec(RepeatMode mode, int repeats) {

    public static final int INFINITE = -1;

    public RepeatSpec {
        if (mode == null) {
            throw new NullPointerException("mode");
        }
        if (repeats < INFINITE) {
            throw new IllegalArgumentException("repeats must be >= -1");
        }
        if (mode == RepeatMode.NONE && repeats != 0) {
            throw new IllegalArgumentException("NONE repeat mode requires repeats == 0");
        }
    }

    public static RepeatSpec none() {
        return new RepeatSpec(RepeatMode.NONE, 0);
    }

    public boolean infinite() {
        return repeats == INFINITE;
    }
}
