package sh.harold.creative.library.curve.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.curve.CatmullRomMode;
import sh.harold.creative.library.curve.CurvePath;
import sh.harold.creative.library.curve.CurvePathSpec;
import sh.harold.creative.library.curve.CurveSegmentSpec;
import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Vec3;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurvePathsTest {

    @Test
    void linePathSamplesByProgressAndDistance() {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.Line(Vec3.ZERO, new Vec3(10.0, 0.0, 0.0))
        )));

        assertEquals(10.0, path.length(), 1.0e-6);
        assertEquals(5.0, path.pointAtProgress(0.5).x(), 1.0e-6);
        assertEquals(7.0, path.pointAtDistance(7.0).x(), 1.0e-6);
    }

    @Test
    void arcResamplesEvenlyAroundCenter() {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CircularArc(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), Vec3.UNIT_Y, Angle.degrees(90.0))
        )));

        List<Vec3> samples = path.resampleEvenly(3);
        assertEquals(1.0, samples.getFirst().x(), 1.0e-6);
        assertEquals(-1.0, samples.getLast().z(), 1.0e-6);
    }

    @Test
    void trimAndReverseProduceDeterministicPolylinePaths() {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CubicBezier(
                        Vec3.ZERO,
                        new Vec3(1.0, 2.0, 0.0),
                        new Vec3(3.0, 2.0, 0.0),
                        new Vec3(4.0, 0.0, 0.0)
                )
        )));

        CurvePath trimmed = path.trim(0.25, 0.75);
        CurvePath reversed = path.reverse();

        assertTrue(trimmed.length() > 0.0);
        assertEquals(path.pointAtProgress(0.0).x(), reversed.pointAtProgress(1.0).x(), 1.0e-6);
    }

    @Test
    void catmullRomPathHasPositiveLength() {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CatmullRom(
                        new Vec3(-1.0, 0.0, 0.0),
                        Vec3.ZERO,
                        new Vec3(2.0, 1.0, 0.0),
                        new Vec3(4.0, 0.0, 0.0),
                        CatmullRomMode.CENTRIPETAL
                )
        )));

        assertTrue(path.length() > 0.0);
        assertTrue(path.bounds().contains(path.pointAtProgress(0.5)));
    }
}
