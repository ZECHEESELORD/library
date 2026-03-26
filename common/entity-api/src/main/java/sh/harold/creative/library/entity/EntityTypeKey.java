package sh.harold.creative.library.entity;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public record EntityTypeKey(Key key, EntityFamily family) {

    public EntityTypeKey {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(family, "family");
    }
}
