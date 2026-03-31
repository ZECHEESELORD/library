package sh.harold.creative.library.curve.core;

import sh.harold.creative.library.curve.CatmullRomMode;
import sh.harold.creative.library.curve.CurvePath;
import sh.harold.creative.library.curve.CurveSegmentSpec;
import sh.harold.creative.library.curve.CurveSplit;
import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class StandardCurvePath implements CurvePath {

    private static final int SEGMENT_SAMPLES = 64;

    private final List<SampledSegment> segments;
    private final double length;
    private final Bounds3 bounds;

    StandardCurvePath(List<CurveSegmentSpec> specs) {
        List<CurveSegmentSpec> value = List.copyOf(Objects.requireNonNull(specs, "specs"));
        this.segments = value.stream().map(SampledSegment::new).toList();
        this.length = segments.stream().mapToDouble(SampledSegment::length).sum();
        this.bounds = segments.stream()
                .map(SampledSegment::bounds)
                .reduce(Bounds3::union)
                .orElseGet(() -> Bounds3.point(Vec3.ZERO));
    }

    @Override
    public Vec3 pointAtProgress(double progress) {
        return pointAtDistance(length * clamp01(progress));
    }

    @Override
    public Vec3 pointAtDistance(double distance) {
        if (segments.size() == 1) {
            return segments.getFirst().pointAtDistance(clamp(distance, 0.0, length));
        }
        double clamped = clamp(distance, 0.0, length);
        double walked = 0.0;
        for (SampledSegment segment : segments) {
            if (clamped <= walked + segment.length()) {
                return segment.pointAtDistance(clamped - walked);
            }
            walked += segment.length();
        }
        return segments.getLast().pointAtDistance(segments.getLast().length());
    }

    @Override
    public Vec3 tangentAtProgress(double progress) {
        return tangentAtDistance(length * clamp01(progress));
    }

    @Override
    public Vec3 tangentAtDistance(double distance) {
        if (segments.size() == 1) {
            return segments.getFirst().tangentAtDistance(clamp(distance, 0.0, length));
        }
        double clamped = clamp(distance, 0.0, length);
        double walked = 0.0;
        for (SampledSegment segment : segments) {
            if (clamped <= walked + segment.length()) {
                return segment.tangentAtDistance(clamped - walked);
            }
            walked += segment.length();
        }
        return segments.getLast().tangentAtDistance(segments.getLast().length());
    }

    @Override
    public double length() {
        return length;
    }

    @Override
    public Bounds3 bounds() {
        return bounds;
    }

    @Override
    public CurveSplit split(double progress) {
        double clamped = clamp01(progress);
        return new CurveSplit(trim(0.0, clamped), trim(clamped, 1.0));
    }

    @Override
    public CurvePath trim(double fromProgress, double toProgress) {
        double start = clamp01(Math.min(fromProgress, toProgress));
        double end = clamp01(Math.max(fromProgress, toProgress));
        if (start == end) {
            Vec3 point = pointAtProgress(start);
            return CurvePaths.polyline(List.of(point, point));
        }
        int sampleCount = Math.max(2, (int) Math.ceil((end - start) * SEGMENT_SAMPLES * Math.max(1, segments.size())) + 1);
        List<Vec3> points = new ArrayList<>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            double u = start + ((end - start) * i / (double) (sampleCount - 1));
            points.add(pointAtProgress(u));
        }
        return CurvePaths.polyline(points);
    }

    @Override
    public CurvePath reverse() {
        List<Vec3> points = resampleEvenly(Math.max(2, segments.size() * SEGMENT_SAMPLES / 4));
        List<Vec3> reversed = new ArrayList<>(points);
        Collections.reverse(reversed);
        return CurvePaths.polyline(reversed);
    }

    @Override
    public List<Vec3> resampleEvenly(int sampleCount) {
        if (sampleCount < 2) {
            throw new IllegalArgumentException("sampleCount must be at least 2");
        }
        List<Vec3> points = new ArrayList<>(sampleCount);
        double spacing = sampleCount == 1 ? 0.0 : length / (sampleCount - 1);
        for (int i = 0; i < sampleCount; i++) {
            points.add(pointAtDistance(spacing * i));
        }
        return List.copyOf(points);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SampledSegment {

        private final ParametricSegment segment;
        private final double[] cumulativeLengths;
        private final Bounds3 bounds;
        private final double length;

        private SampledSegment(CurveSegmentSpec spec) {
            this.segment = ParametricSegment.of(spec);
            this.cumulativeLengths = new double[SEGMENT_SAMPLES + 1];
            Vec3 previous = segment.point(0.0);
            Bounds3 runningBounds = Bounds3.point(previous);
            double walked = 0.0;
            for (int i = 1; i <= SEGMENT_SAMPLES; i++) {
                double u = i / (double) SEGMENT_SAMPLES;
                Vec3 point = segment.point(u);
                walked += previous.distance(point);
                cumulativeLengths[i] = walked;
                previous = point;
                runningBounds = runningBounds.union(Bounds3.point(point));
            }
            this.length = walked;
            this.bounds = runningBounds;
        }

        private double length() {
            return length;
        }

        private Bounds3 bounds() {
            return bounds;
        }

        private Vec3 pointAtDistance(double distance) {
            return segment.point(resolveU(distance));
        }

        private Vec3 tangentAtDistance(double distance) {
            return segment.tangent(resolveU(distance));
        }

        private double resolveU(double distance) {
            if (length == 0.0) {
                return 0.0;
            }
            double clamped = clamp(distance, 0.0, length);
            int index = 0;
            while (index < cumulativeLengths.length && cumulativeLengths[index] < clamped) {
                index++;
            }
            if (index == 0) {
                return 0.0;
            }
            if (index >= cumulativeLengths.length) {
                return 1.0;
            }
            double fromLength = cumulativeLengths[index - 1];
            double toLength = cumulativeLengths[index];
            double ratio = toLength == fromLength ? 0.0 : (clamped - fromLength) / (toLength - fromLength);
            return ((index - 1) + ratio) / SEGMENT_SAMPLES;
        }
    }

    private interface ParametricSegment {

        Vec3 point(double u);

        Vec3 tangent(double u);

        static ParametricSegment of(CurveSegmentSpec spec) {
            return switch (spec) {
                case CurveSegmentSpec.Line line -> new LineSegment(line.start(), line.end());
                case CurveSegmentSpec.QuadraticBezier quadratic ->
                        new QuadraticSegment(quadratic.p0(), quadratic.p1(), quadratic.p2());
                case CurveSegmentSpec.CubicBezier cubic ->
                        new CubicSegment(cubic.p0(), cubic.p1(), cubic.p2(), cubic.p3());
                case CurveSegmentSpec.CatmullRom catmull ->
                        new CatmullSegment(catmull.p0(), catmull.p1(), catmull.p2(), catmull.p3(), catmull.mode());
                case CurveSegmentSpec.CircularArc arc ->
                        new ArcSegment(arc.center(), arc.start(), arc.normal(), arc.sweep().radians());
            };
        }
    }

    private record LineSegment(Vec3 start, Vec3 end) implements ParametricSegment {

        @Override
        public Vec3 point(double u) {
            return start.add(end.subtract(start).multiply(u));
        }

        @Override
        public Vec3 tangent(double u) {
            return end.subtract(start).normalize();
        }
    }

    private record QuadraticSegment(Vec3 p0, Vec3 p1, Vec3 p2) implements ParametricSegment {

        @Override
        public Vec3 point(double u) {
            double oneMinus = 1.0 - u;
            return p0.multiply(oneMinus * oneMinus)
                    .add(p1.multiply(2.0 * oneMinus * u))
                    .add(p2.multiply(u * u));
        }

        @Override
        public Vec3 tangent(double u) {
            return p1.subtract(p0).multiply(2.0 * (1.0 - u))
                    .add(p2.subtract(p1).multiply(2.0 * u))
                    .normalize();
        }
    }

    private record CubicSegment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) implements ParametricSegment {

        @Override
        public Vec3 point(double u) {
            double oneMinus = 1.0 - u;
            return p0.multiply(oneMinus * oneMinus * oneMinus)
                    .add(p1.multiply(3.0 * oneMinus * oneMinus * u))
                    .add(p2.multiply(3.0 * oneMinus * u * u))
                    .add(p3.multiply(u * u * u));
        }

        @Override
        public Vec3 tangent(double u) {
            double oneMinus = 1.0 - u;
            return p1.subtract(p0).multiply(3.0 * oneMinus * oneMinus)
                    .add(p2.subtract(p1).multiply(6.0 * oneMinus * u))
                    .add(p3.subtract(p2).multiply(3.0 * u * u))
                    .normalize();
        }
    }

    private record ArcSegment(Vec3 center, Vec3 start, Vec3 normal, double sweepRadians) implements ParametricSegment {

        @Override
        public Vec3 point(double u) {
            return center.add(rotate(start.subtract(center), normal.normalize(), sweepRadians * u));
        }

        @Override
        public Vec3 tangent(double u) {
            Vec3 radius = rotate(start.subtract(center), normal.normalize(), sweepRadians * u);
            double direction = sweepRadians < 0.0 ? -1.0 : 1.0;
            return normal.normalize().cross(radius).multiply(direction).normalize();
        }
    }

    private static final class CatmullSegment implements ParametricSegment {

        private final Vec3 p0;
        private final Vec3 p1;
        private final Vec3 p2;
        private final Vec3 p3;
        private final double alpha;

        private CatmullSegment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, CatmullRomMode mode) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.alpha = mode.alpha();
        }

        @Override
        public Vec3 point(double u) {
            double t0 = 0.0;
            double t1 = nextT(t0, p0, p1);
            double t2 = nextT(t1, p1, p2);
            double t3 = nextT(t2, p2, p3);
            double t = t1 + ((t2 - t1) * u);

            Vec3 a1 = interpolate(p0, p1, safeDivide(t - t0, t1 - t0));
            Vec3 a2 = interpolate(p1, p2, safeDivide(t - t1, t2 - t1));
            Vec3 a3 = interpolate(p2, p3, safeDivide(t - t2, t3 - t2));

            Vec3 b1 = interpolate(a1, a2, safeDivide(t - t0, t2 - t0));
            Vec3 b2 = interpolate(a2, a3, safeDivide(t - t1, t3 - t1));
            return interpolate(b1, b2, safeDivide(t - t1, t2 - t1));
        }

        @Override
        public Vec3 tangent(double u) {
            double epsilon = 1.0 / SEGMENT_SAMPLES;
            double before = Math.max(0.0, u - epsilon);
            double after = Math.min(1.0, u + epsilon);
            return point(after).subtract(point(before)).normalize();
        }

        private double nextT(double previousT, Vec3 from, Vec3 to) {
            return previousT + Math.pow(Math.max(from.distance(to), 1.0e-6), alpha);
        }
    }

    private static Vec3 interpolate(Vec3 from, Vec3 to, double progress) {
        return from.add(to.subtract(from).multiply(progress));
    }

    private static double safeDivide(double numerator, double denominator) {
        if (Math.abs(denominator) < 1.0e-9) {
            return 0.0;
        }
        return numerator / denominator;
    }

    private static Vec3 rotate(Vec3 vector, Vec3 axis, double radians) {
        Vec3 unitAxis = axis.normalize();
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return vector.multiply(cos)
                .add(unitAxis.cross(vector).multiply(sin))
                .add(unitAxis.multiply(unitAxis.dot(vector) * (1.0 - cos)));
    }
}
