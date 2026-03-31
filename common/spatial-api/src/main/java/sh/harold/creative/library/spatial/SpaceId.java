package sh.harold.creative.library.spatial;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public record SpaceId(Key key) {

    public SpaceId {
        key = Objects.requireNonNull(key, "key");
    }

    public static SpaceId of(Key key) {
        return new SpaceId(key);
    }

    public static SpaceId of(String namespace, String value) {
        return new SpaceId(Key.key(namespace, value));
    }
}
