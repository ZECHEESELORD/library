package sh.harold.creative.library.sound.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.sound.PackRegistrationResult;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCuePack;
import sh.harold.creative.library.sound.SoundCueRegistry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class StandardSoundCueRegistry implements SoundCueRegistry {

    private final Map<Key, SoundCue> cues = new LinkedHashMap<>();

    @Override
    public synchronized PackRegistrationResult register(SoundCuePack pack) {
        return apply(Objects.requireNonNull(pack, "pack"), false);
    }

    @Override
    public synchronized PackRegistrationResult overlay(SoundCuePack pack) {
        return apply(Objects.requireNonNull(pack, "pack"), true);
    }

    @Override
    public synchronized Optional<SoundCue> find(Key cueKey) {
        return Optional.ofNullable(cues.get(Objects.requireNonNull(cueKey, "cueKey")));
    }

    private PackRegistrationResult apply(SoundCuePack pack, boolean allowReplace) {
        Set<Key> addedKeys = new LinkedHashSet<>();
        Set<Key> replacedKeys = new LinkedHashSet<>();
        for (Map.Entry<Key, SoundCue> entry : pack.cues().entrySet()) {
            Key key = entry.getKey();
            boolean exists = cues.containsKey(key);
            if (exists && !allowReplace) {
                throw new IllegalArgumentException("Duplicate sound cue key: " + key.asString());
            }
            cues.put(key, entry.getValue());
            addedKeys.add(key);
            if (exists) {
                replacedKeys.add(key);
            }
        }
        return new PackRegistrationResult(pack.namespace(), addedKeys, replacedKeys);
    }
}
