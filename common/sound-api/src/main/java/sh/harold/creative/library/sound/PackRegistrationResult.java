package sh.harold.creative.library.sound;

import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record PackRegistrationResult(
        String namespace,
        Set<Key> addedKeys,
        Set<Key> replacedKeys
) {

    public PackRegistrationResult {
        Objects.requireNonNull(namespace, "namespace");
        addedKeys = immutableKeys(addedKeys, "addedKeys");
        replacedKeys = immutableKeys(replacedKeys, "replacedKeys");
    }

    public boolean replacedAnything() {
        return !replacedKeys.isEmpty();
    }

    private static Set<Key> immutableKeys(Set<Key> keys, String name) {
        Objects.requireNonNull(keys, name);
        LinkedHashSet<Key> copy = new LinkedHashSet<>();
        for (Key key : keys) {
            copy.add(Objects.requireNonNull(key, name + " entry"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
