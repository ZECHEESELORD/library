package sh.harold.creative.library.sound;

import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SoundCuePack(String namespace, Map<Key, SoundCue> cues) {

    public SoundCuePack {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace cannot be blank");
        }
        Key.key(namespace, "pack");

        Objects.requireNonNull(cues, "cues");
        LinkedHashMap<Key, SoundCue> copy = new LinkedHashMap<>();
        for (Map.Entry<Key, SoundCue> entry : cues.entrySet()) {
            Key key = Objects.requireNonNull(entry.getKey(), "cue key");
            SoundCue cue = Objects.requireNonNull(entry.getValue(), "cue");
            if (!key.namespace().equals(namespace)) {
                throw new IllegalArgumentException("Cue key namespace " + key.namespace() + " does not match pack namespace " + namespace);
            }
            copy.put(key, cue);
        }
        cues = Collections.unmodifiableMap(copy);
    }
}
