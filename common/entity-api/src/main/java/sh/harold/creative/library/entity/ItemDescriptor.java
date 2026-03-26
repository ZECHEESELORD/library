package sh.harold.creative.library.entity;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public record ItemDescriptor(Key key, int amount) {

    public ItemDescriptor {
        Objects.requireNonNull(key, "key");
        if (amount < 1) {
            throw new IllegalArgumentException("Item amount must be positive");
        }
    }
}
