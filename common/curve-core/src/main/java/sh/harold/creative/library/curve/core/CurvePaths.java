package sh.harold.creative.library.curve.core;

import sh.harold.creative.library.curve.CurvePath;
import sh.harold.creative.library.curve.CurvePathSpec;
import sh.harold.creative.library.curve.CurveSegmentSpec;
import sh.harold.creative.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CurvePaths {

    private CurvePaths() {
    }

    public static CurvePath create(CurvePathSpec spec) {
        CurvePathSpec value = Objects.requireNonNull(spec, "spec");
        return new StandardCurvePath(value.segments());
    }

    public static CurvePath polyline(List<Vec3> points) {
        List<Vec3> value = List.copyOf(Objects.requireNonNull(points, "points"));
        if (value.size() < 2) {
            throw new IllegalArgumentException("polyline requires at least two points");
        }
        List<CurveSegmentSpec> segments = new ArrayList<>(value.size() - 1);
        for (int i = 0; i < value.size() - 1; i++) {
            segments.add(new CurveSegmentSpec.Line(value.get(i), value.get(i + 1)));
        }
        return new StandardCurvePath(segments);
    }
}
