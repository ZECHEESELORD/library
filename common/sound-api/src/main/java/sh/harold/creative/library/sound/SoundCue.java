package sh.harold.creative.library.sound;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;

import java.util.List;
import java.util.Objects;

public sealed interface SoundCue permits SoundCue.Layer, SoundCue.Sequence, SoundCue.Silent, SoundCue.SoundEffect, SoundCue.Variant {

    default CuePlayback play(Audience audience, SoundCueService service) {
        Objects.requireNonNull(audience, "audience");
        return Objects.requireNonNull(service, "service").play(audience, this);
    }

    record SoundEffect(Sound sound) implements SoundCue {

        public SoundEffect {
            Objects.requireNonNull(sound, "sound");
        }
    }

    record Sequence(List<CueStep> steps) implements SoundCue {

        public Sequence {
            steps = copyOf(steps, "steps");
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("steps cannot be empty");
            }
        }
    }

    record Layer(List<SoundCue> cues) implements SoundCue {

        public Layer {
            cues = copyOf(cues, "cues");
            if (cues.isEmpty()) {
                throw new IllegalArgumentException("cues cannot be empty");
            }
        }
    }

    record Variant(List<SoundCue> variants) implements SoundCue {

        public Variant {
            variants = copyOf(variants, "variants");
            if (variants.isEmpty()) {
                throw new IllegalArgumentException("variants cannot be empty");
            }
        }
    }

    record Silent() implements SoundCue {
    }

    private static <T> List<T> copyOf(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        return List.copyOf(values);
    }
}
