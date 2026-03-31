package sh.harold.creative.library.spatial.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Segment3;
import sh.harold.creative.library.spatial.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeShapesTest {

    @Test
    void aabbContainsAndMeasuresBoundaryDistance() {
        AabbVolume volume = new AabbVolume(new Bounds3(new Vec3(-1.0, -1.0, -1.0), new Vec3(1.0, 1.0, 1.0)));

        assertTrue(volume.contains(Vec3.ZERO));
        assertEquals(1.0, volume.distanceToBoundary(Vec3.ZERO), 1.0e-6);
        assertFalse(volume.contains(new Vec3(2.0, 0.0, 0.0)));
        assertEquals(1.0, volume.distance(new Vec3(2.0, 0.0, 0.0)), 1.0e-6);
    }

    @Test
    void sphereSignedDistanceUsesBoundary() {
        SphereVolume volume = new SphereVolume(Vec3.ZERO, 3.0);

        assertEquals(-3.0, volume.signedDistance(Vec3.ZERO), 1.0e-6);
        assertEquals(1.0, volume.signedDistance(new Vec3(4.0, 0.0, 0.0)), 1.0e-6);
    }

    @Test
    void orientedBoxRespectsFrameRotation() {
        OrientedBoxVolume volume = new OrientedBoxVolume(
                Frame3.of(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), Vec3.UNIT_Y),
                new Vec3(2.0, 1.0, 0.5)
        );

        assertTrue(volume.contains(new Vec3(0.0, 0.0, 1.5)));
        assertFalse(volume.contains(new Vec3(1.0, 0.0, 1.5)));
    }

    @Test
    void capsuleMeasuresDistanceAroundSegment() {
        CapsuleVolume volume = new CapsuleVolume(new Segment3(new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 5.0)), 1.0);

        assertTrue(volume.contains(new Vec3(0.5, 0.0, 2.0)));
        assertEquals(1.0, volume.distanceToBoundary(new Vec3(0.0, 0.0, 2.0)), 1.0e-6);
        assertEquals(1.0, volume.distance(new Vec3(2.0, 0.0, 2.0)), 1.0e-6);
    }
}
