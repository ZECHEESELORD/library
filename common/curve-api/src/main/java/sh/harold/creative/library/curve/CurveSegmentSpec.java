package sh.harold.creative.library.curve;

import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public sealed interface CurveSegmentSpec permits CurveSegmentSpec.CatmullRom, CurveSegmentSpec.CircularArc,
        CurveSegmentSpec.CubicBezier, CurveSegmentSpec.Line, CurveSegmentSpec.QuadraticBezier {

    record Line(Vec3 start, Vec3 end) implements CurveSegmentSpec {

        public Line {
            start = Objects.requireNonNull(start, "start");
            end = Objects.requireNonNull(end, "end");
        }
    }

    record QuadraticBezier(Vec3 p0, Vec3 p1, Vec3 p2) implements CurveSegmentSpec {

        public QuadraticBezier {
            p0 = Objects.requireNonNull(p0, "p0");
            p1 = Objects.requireNonNull(p1, "p1");
            p2 = Objects.requireNonNull(p2, "p2");
        }
    }

    record CubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) implements CurveSegmentSpec {

        public CubicBezier {
            p0 = Objects.requireNonNull(p0, "p0");
            p1 = Objects.requireNonNull(p1, "p1");
            p2 = Objects.requireNonNull(p2, "p2");
            p3 = Objects.requireNonNull(p3, "p3");
        }
    }

    record CatmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, CatmullRomMode mode) implements CurveSegmentSpec {

        public CatmullRom {
            p0 = Objects.requireNonNull(p0, "p0");
            p1 = Objects.requireNonNull(p1, "p1");
            p2 = Objects.requireNonNull(p2, "p2");
            p3 = Objects.requireNonNull(p3, "p3");
            mode = Objects.requireNonNull(mode, "mode");
        }
    }

    record CircularArc(Vec3 center, Vec3 start, Vec3 normal, Angle sweep) implements CurveSegmentSpec {

        public CircularArc {
            center = Objects.requireNonNull(center, "center");
            start = Objects.requireNonNull(start, "start");
            normal = Objects.requireNonNull(normal, "normal");
            sweep = Objects.requireNonNull(sweep, "sweep");
            if (start.subtract(center).isZero(1.0e-9)) {
                throw new IllegalArgumentException("start must not equal center");
            }
            if (normal.isZero(1.0e-9)) {
                throw new IllegalArgumentException("normal cannot be zero");
            }
        }
    }
}
