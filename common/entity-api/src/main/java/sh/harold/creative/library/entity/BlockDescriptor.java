package sh.harold.creative.library.entity;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public record BlockDescriptor(Key key) {

    public BlockDescriptor {
        Objects.requireNonNull(key, "key");
    }
}
