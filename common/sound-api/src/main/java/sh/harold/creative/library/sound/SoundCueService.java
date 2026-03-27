package sh.harold.creative.library.sound;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;

import java.util.Objects;

public interface SoundCueService extends AutoCloseable {

    SoundCueRegistry registry();

    CuePlayback play(Audience audience, SoundCue cue);

    default CuePlayback play(Audience audience, Key cueKey) {
        Objects.requireNonNull(cueKey, "cueKey");
        return play(audience, registry().cue(cueKey));
    }

    default CuePlayback play(Audience audience, String cueKey) {
        return play(audience, Key.key(Objects.requireNonNull(cueKey, "cueKey")));
    }

    @Override
    void close();
}
