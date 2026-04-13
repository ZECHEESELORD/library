package sh.harold.creative.library.sound;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;

import java.util.Objects;

public interface SoundCueService extends AutoCloseable {

    SoundCueRegistry registry();

    CuePlayback play(SoundTarget target, SoundCue cue);

    default CuePlayback play(Audience audience, SoundCue cue) {
        return play(SoundTarget.audience(Objects.requireNonNull(audience, "audience")), cue);
    }

    default CuePlayback play(SoundTarget target, Key cueKey) {
        Objects.requireNonNull(cueKey, "cueKey");
        return play(Objects.requireNonNull(target, "target"), registry().cue(cueKey));
    }

    default CuePlayback play(Audience audience, Key cueKey) {
        return play(SoundTarget.audience(Objects.requireNonNull(audience, "audience")), cueKey);
    }

    default CuePlayback play(SoundTarget target, String cueKey) {
        return play(Objects.requireNonNull(target, "target"), Key.key(Objects.requireNonNull(cueKey, "cueKey")));
    }

    default CuePlayback play(Audience audience, String cueKey) {
        return play(SoundTarget.audience(Objects.requireNonNull(audience, "audience")), cueKey);
    }

    @Override
    void close();
}
