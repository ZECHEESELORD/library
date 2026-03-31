package sh.harold.creative.library.impulse;

import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public sealed interface ImpulseVector permits ImpulseVector.AwayFromPoint, ImpulseVector.LocalHorizontal, ImpulseVector.LocalLook,
        ImpulseVector.RadialFromOrigin, ImpulseVector.TowardPoint, ImpulseVector.World {

    record World(Vec3 vector) implements ImpulseVector {

        public World {
            vector = Objects.requireNonNull(vector, "vector");
        }
    }

    record LocalLook(Vec3 vector) implements ImpulseVector {

        public LocalLook {
            vector = Objects.requireNonNull(vector, "vector");
        }
    }

    record LocalHorizontal(Vec3 vector) implements ImpulseVector {

        public LocalHorizontal {
            vector = Objects.requireNonNull(vector, "vector");
        }
    }

    record TowardPoint(Vec3 point, double strength) implements ImpulseVector {

        public TowardPoint {
            point = Objects.requireNonNull(point, "point");
            validateStrength(strength);
        }
    }

    record AwayFromPoint(Vec3 point, double strength) implements ImpulseVector {

        public AwayFromPoint {
            point = Objects.requireNonNull(point, "point");
            validateStrength(strength);
        }
    }

    record RadialFromOrigin(Vec3 origin, double strength) implements ImpulseVector {

        public RadialFromOrigin {
            origin = Objects.requireNonNull(origin, "origin");
            validateStrength(strength);
        }
    }

    private static void validateStrength(double strength) {
        if (!Double.isFinite(strength) || strength < 0.0) {
            throw new IllegalArgumentException("strength must be finite and non-negative");
        }
    }
}
