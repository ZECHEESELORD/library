package sh.harold.library.movement;

import java.util.Locale;

/** 02's three variable-jump presets: one code path, different numbers. */
public enum JumpPreset {
    /** Boost: a tap is vanilla; holding lowers gravity while rising on air ticks 3 to 10. */
    A,
    /** Charge: holding on the ground squats up to 8 ticks; release launches higher. */
    B,
    /** Cut: holding is vanilla; releasing while rising cuts upward speed once. */
    C;

    public static JumpPreset parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
