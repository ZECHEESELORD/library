package sh.harold.creative.library.sound;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.List;
import java.util.Objects;

public final class SoundCues {

    private static final SoundCue SILENT = new SoundCue.Silent();

    private SoundCues() {
    }

    public static SoundCue sound(String soundKey, float volume, float pitch) {
        return sound(Key.key(Objects.requireNonNull(soundKey, "soundKey")), Sound.Source.MASTER, volume, pitch);
    }

    public static SoundCue sound(String soundKey, Sound.Source source, float volume, float pitch) {
        return sound(Key.key(Objects.requireNonNull(soundKey, "soundKey")), source, volume, pitch);
    }

    public static SoundCue sound(Key soundKey, float volume, float pitch) {
        return sound(soundKey, Sound.Source.MASTER, volume, pitch);
    }

    public static SoundCue sound(Key soundKey, Sound.Source source, float volume, float pitch) {
        Objects.requireNonNull(soundKey, "soundKey");
        Objects.requireNonNull(source, "source");
        return sound(Sound.sound(soundKey, source, volume, pitch));
    }

    public static SoundCue sound(Sound sound) {
        return new SoundCue.SoundEffect(Objects.requireNonNull(sound, "sound"));
    }

    public static CueStep atTick(long tick, SoundCue cue) {
        return new CueStep(tick, cue);
    }

    public static SoundCue sequence(CueStep... steps) {
        Objects.requireNonNull(steps, "steps");
        return new SoundCue.Sequence(List.of(steps));
    }

    public static SoundCue layer(SoundCue... cues) {
        Objects.requireNonNull(cues, "cues");
        return new SoundCue.Layer(List.of(cues));
    }

    public static SoundCue oneOf(SoundCue... variants) {
        Objects.requireNonNull(variants, "variants");
        return new SoundCue.Variant(List.of(variants));
    }

    public static SoundCue silent() {
        return SILENT;
    }
}
