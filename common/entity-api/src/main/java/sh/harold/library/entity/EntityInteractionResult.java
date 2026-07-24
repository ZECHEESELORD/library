package sh.harold.library.entity;

import java.util.Objects;

public enum EntityInteractionResult {
    PASS,
    CONSUME;

    public EntityInteractionResult or(EntityInteractionResult other) {
        return this == CONSUME || Objects.requireNonNull(other, "other") == CONSUME ? CONSUME : PASS;
    }
}
