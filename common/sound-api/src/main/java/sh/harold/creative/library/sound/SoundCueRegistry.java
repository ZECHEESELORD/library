package sh.harold.creative.library.sound;

import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.Optional;

public interface SoundCueRegistry {

    PackRegistrationResult register(SoundCuePack pack);

    PackRegistrationResult overlay(SoundCuePack pack);

    Optional<SoundCue> find(Key cueKey);

    default Optional<SoundCue> find(String cueKey) {
        return find(Key.key(Objects.requireNonNull(cueKey, "cueKey")));
    }

    default SoundCue cue(Key cueKey) {
        Key key = Objects.requireNonNull(cueKey, "cueKey");
        return find(key).orElseThrow(() -> new IllegalArgumentException("Unknown sound cue: " + key.asString()));
    }

    default SoundCue cue(String cueKey) {
        return cue(Key.key(Objects.requireNonNull(cueKey, "cueKey")));
    }
}
