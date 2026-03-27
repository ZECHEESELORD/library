package sh.harold.creative.library.sound;

import java.util.Objects;

public record CueStep(long tick, SoundCue cue) {

    public CueStep {
        if (tick < 0) {
            throw new IllegalArgumentException("tick cannot be negative");
        }
        Objects.requireNonNull(cue, "cue");
    }
}
