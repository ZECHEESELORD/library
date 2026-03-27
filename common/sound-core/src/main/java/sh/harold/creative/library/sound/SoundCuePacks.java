package sh.harold.creative.library.sound;

import net.kyori.adventure.key.Key;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SoundCuePacks {

    private SoundCuePacks() {
    }

    public static Builder pack(String namespace) {
        return new Builder(namespace);
    }

    public static final class Builder {

        private final String namespace;
        private final Map<Key, SoundCue> cues = new LinkedHashMap<>();

        private Builder(String namespace) {
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            Key.key(namespace, "pack");
        }

        public Builder cue(String path, SoundCue cue) {
            Objects.requireNonNull(path, "path");
            return cue(Key.key(namespace, path), cue);
        }

        public Builder cue(Key key, SoundCue cue) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(cue, "cue");
            if (!key.namespace().equals(namespace)) {
                throw new IllegalArgumentException("Cue key namespace " + key.namespace() + " does not match builder namespace " + namespace);
            }
            if (cues.putIfAbsent(key, cue) != null) {
                throw new IllegalArgumentException("Duplicate cue key: " + key.asString());
            }
            return this;
        }

        public SoundCuePack build() {
            return new SoundCuePack(namespace, cues);
        }
    }
}
