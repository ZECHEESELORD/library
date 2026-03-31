package sh.harold.creative.library.impulse;

import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;
import java.util.Optional;

public record ComposedImpulse(Vec3 additiveVelocity, Optional<Vec3> absoluteVelocity) {

    public ComposedImpulse {
        additiveVelocity = Objects.requireNonNull(additiveVelocity, "additiveVelocity");
        absoluteVelocity = Objects.requireNonNull(absoluteVelocity, "absoluteVelocity");
    }

    public static ComposedImpulse none() {
        return new ComposedImpulse(Vec3.ZERO, Optional.empty());
    }
}
