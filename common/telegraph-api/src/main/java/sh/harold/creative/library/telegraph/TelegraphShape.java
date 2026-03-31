package sh.harold.creative.library.telegraph;

import sh.harold.creative.library.curve.CurvePath;
import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public sealed interface TelegraphShape permits TelegraphShape.Arc, TelegraphShape.CapsuleCorridor, TelegraphShape.Circle,
        TelegraphShape.Cone, TelegraphShape.LineRibbon, TelegraphShape.PathRibbon, TelegraphShape.Rectangle, TelegraphShape.Ring {

    record Circle(double radius) implements TelegraphShape {

        public Circle {
            validate(radius, "radius");
        }
    }

    record Ring(double radius, double halfThickness) implements TelegraphShape {

        public Ring {
            validate(radius, "radius");
            validate(halfThickness, "halfThickness");
        }
    }

    record Rectangle(double halfWidth, double halfDepth) implements TelegraphShape {

        public Rectangle {
            validate(halfWidth, "halfWidth");
            validate(halfDepth, "halfDepth");
        }
    }

    record LineRibbon(Vec3 start, Vec3 end, double halfThickness) implements TelegraphShape {

        public LineRibbon {
            start = Objects.requireNonNull(start, "start");
            end = Objects.requireNonNull(end, "end");
            validate(halfThickness, "halfThickness");
        }
    }

    record CapsuleCorridor(Vec3 start, Vec3 end, double radius) implements TelegraphShape {

        public CapsuleCorridor {
            start = Objects.requireNonNull(start, "start");
            end = Objects.requireNonNull(end, "end");
            validate(radius, "radius");
        }
    }

    record Cone(double radius, Angle halfAngle) implements TelegraphShape {

        public Cone {
            validate(radius, "radius");
            halfAngle = Objects.requireNonNull(halfAngle, "halfAngle");
        }
    }

    record Arc(double radius, double halfThickness, Angle startAngle, Angle sweep) implements TelegraphShape {

        public Arc {
            validate(radius, "radius");
            validate(halfThickness, "halfThickness");
            startAngle = Objects.requireNonNull(startAngle, "startAngle");
            sweep = Objects.requireNonNull(sweep, "sweep");
        }
    }

    record PathRibbon(CurvePath path, double halfThickness) implements TelegraphShape {

        public PathRibbon {
            path = Objects.requireNonNull(path, "path");
            validate(halfThickness, "halfThickness");
        }
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
