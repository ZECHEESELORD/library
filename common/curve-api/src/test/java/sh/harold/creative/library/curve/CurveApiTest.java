package sh.harold.creative.library.curve;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Vec3;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurveApiTest {

    @Test
    void pathSpecCopiesSegments() {
        CurvePathSpec spec = new CurvePathSpec(List.of(
                new CurveSegmentSpec.Line(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0))
        ));

        assertEquals(1, spec.segments().size());
    }

    @Test
    void circularArcSpecStoresSweep() {
        CurveSegmentSpec.CircularArc arc = new CurveSegmentSpec.CircularArc(
                Vec3.ZERO,
                new Vec3(1.0, 0.0, 0.0),
                Vec3.UNIT_Y,
                Angle.degrees(90.0)
        );

        assertEquals(90.0, arc.sweep().degrees(), 1.0e-9);
    }
}
