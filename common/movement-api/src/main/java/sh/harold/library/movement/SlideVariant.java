package sh.harold.library.movement;

import java.util.Locale;

/** 02's two slide inputs. */
public enum SlideVariant {
    /** Press sneak while sprinting on the ground. */
    S1,
    /** Press sneak and jump within the chord window while sprinting; the jump fires and is cancelled into the slide. */
    S2;

    public static SlideVariant parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
