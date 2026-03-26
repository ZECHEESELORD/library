package sh.harold.creative.library.entity.core;

import sh.harold.creative.library.entity.EntitySpec;

import java.util.Objects;

public final class EntitySpecValidator {

    private EntitySpecValidator() {
    }

    public static void validate(EntitySpec spec) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(spec.type(), "spec.type");
        Objects.requireNonNull(spec.transform(), "spec.transform");
        Objects.requireNonNull(spec.flags(), "spec.flags");
        Objects.requireNonNull(spec.tags(), "spec.tags");
    }
}
