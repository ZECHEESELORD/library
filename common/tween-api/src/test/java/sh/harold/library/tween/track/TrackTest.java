package sh.harold.library.tween.track;

import org.junit.jupiter.api.Test;
import sh.harold.library.spatial.Angle;
import sh.harold.library.spatial.Quaternion;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tween.Easing;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackTest {

    private static final double EPS = 1.0e-9;

    @Test
    void hermiteMatchesCatalogFormulaWithExplicitTangents() {
        Track<Double> track = Track.scalar()
                .hermite(2.0, 1.0, Tangent.flat(), Tangent.explicit(0.5))
                .hermite(6.0, 3.0, Tangent.explicit(-0.25), Tangent.flat())
                .build();
        double h = 4.0;
        for (double t = 2.0; t <= 6.0; t += 0.37) {
            double u = (t - 2.0) / h;
            double expected = ((2 * u * u * u) - (3 * u * u) + 1) * 1.0
                    + ((u * u * u) - (2 * u * u) + u) * h * 0.5
                    + ((-2 * u * u * u) + (3 * u * u)) * 3.0
                    + ((u * u * u) - (u * u)) * h * -0.25;
            assertEquals(expected, track.sample(t), EPS, "t=" + t);
        }
    }

    @Test
    void autoTangentIsCatmullRomAndPassesThroughKeys() {
        Track<Double> track = Track.scalar()
                .hermite(0.0, 0.0)
                .hermite(2.0, 4.0)
                .hermite(6.0, 2.0)
                .build();
        assertEquals(0.0, track.sample(0.0), EPS);
        assertEquals(4.0, track.sample(2.0), EPS);
        assertEquals(2.0, track.sample(6.0), EPS);
        // Catmull-Rom slope at the middle key: (2 - 0) / (6 - 0).
        double slope = (track.sample(2.0 + 1.0e-6) - track.sample(2.0 - 1.0e-6)) / 2.0e-6;
        assertEquals(2.0 / 6.0, slope, 1.0e-5);
        // Holds outside the keys.
        assertEquals(0.0, track.sample(-5.0), EPS);
        assertEquals(2.0, track.sample(99.0), EPS);
    }

    @Test
    void linearTangentsBetweenTwoKeysGiveAStraightLine() {
        Track<Double> track = Track.scalar()
                .hermite(0.0, 0.0, Tangent.linear(), Tangent.linear())
                .hermite(10.0, 5.0, Tangent.linear(), Tangent.linear())
                .build();
        assertEquals(1.25, track.sample(2.5), EPS);
        assertEquals(3.5, track.sample(7.0), EPS);
    }

    @Test
    void easedSegmentUsesItsEasing() {
        Track<Double> track = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_IN)
                .eased(4.0, 8.0, Easing.LINEAR)
                .build();
        assertEquals(8.0 * 0.25, track.sample(2.0), EPS);
    }

    @Test
    void steppedKeysHoldForWholeTicksAtAnyFrameRate() {
        Track<Double> track = Track.scalar()
                .stepped(0.0, 1.0)
                .stepped(2.0, 5.0)
                .stepped(3.0, 9.0)
                .build();
        assertEquals(1.0, track.sample(1.999), EPS);
        assertEquals(5.0, track.sample(2.0), EPS);
        assertEquals(5.0, track.sample(2.75), EPS);
        assertEquals(9.0, track.sample(3.0), EPS);
    }

    @Test
    void cutIsExactAndNoFrameBlendsTheTwoSides() {
        double cut = 5.0;
        Track<Double> withCut = Track.scalar()
                .hermite(0.0, 0.0)
                .key(TrackKey.cut(cut, 10.0, -3.0, Interpolation.HERMITE))
                .hermite(10.0, 0.0)
                .build();
        Track<Double> leftOnly = Track.scalar().hermite(0.0, 0.0).hermite(cut, 10.0).build();
        Track<Double> rightOnly = Track.scalar().hermite(cut, -3.0).hermite(10.0, 0.0).build();

        // Every frame at 60, 144 and 240 fps (and odd offsets) matches one side exactly.
        for (int fps : new int[] {60, 144, 240}) {
            for (int frame = 0; frame <= 10 * fps / 20; frame++) {
                double t = frame * (20.0 / fps) + 0.013;
                double expected = t < cut ? leftOnly.sample(t) : rightOnly.sample(t);
                assertEquals(expected, withCut.sample(t), 1.0e-12, "fps=" + fps + " t=" + t);
            }
        }
        assertEquals(-3.0, withCut.sample(cut), 0.0);
        assertEquals(10.0, withCut.sample(Math.nextDown(cut)), 1.0e-9);
        assertEquals(List.of(cut), withCut.cutTimes());
        assertEquals(List.of(cut), withCut.cutsCrossed(4.9, 5.0));
        assertTrue(withCut.cutsCrossed(5.0, 6.0).isEmpty());
    }

    @Test
    void cutOnSteppedTrackShowsLeftUntilTheCutTick() {
        Track<Double> track = Track.scalar()
                .stepped(0.0, 1.0)
                .key(TrackKey.cut(3.0, 1.0, 7.0, Interpolation.STEPPED))
                .build();
        assertEquals(1.0, track.sample(2.99), 0.0);
        assertEquals(7.0, track.sample(3.0), 0.0);
    }

    @Test
    void anglesAreUnwound() {
        Track<Angle> roll = Track.builder(TrackValues.UNWOUND_ANGLE)
                .eased(0.0, Angle.degrees(0.0), Easing.LINEAR)
                .eased(10.0, Angle.degrees(720.0), Easing.LINEAR)
                .build();
        assertEquals(360.0, roll.sample(5.0).degrees(), 1.0e-9);
        assertEquals(180.0, roll.sample(2.5).degrees(), 1.0e-9);
    }

    @Test
    void quaternionEasedKeysSlerp() {
        Quaternion start = Quaternion.IDENTITY;
        Quaternion end = Quaternion.axisAngle(Vec3.UNIT_Y, Math.PI / 2.0);
        Track<Quaternion> track = Track.builder(TrackValues.QUATERNION)
                .eased(0.0, start, Easing.LINEAR)
                .eased(10.0, end, Easing.LINEAR)
                .build();
        Quaternion mid = track.sample(5.0);
        assertEquals(0.0, mid.angleTo(Quaternion.axisAngle(Vec3.UNIT_Y, Math.PI / 4.0)), 1.0e-9);
        assertEquals(1.0, mid.lengthSquared(), 1.0e-9);
    }

    @Test
    void quaternionHermitePassesThroughKeysAndStaysUnit() {
        Quaternion a = Quaternion.IDENTITY;
        Quaternion b = Quaternion.axisAngle(Vec3.UNIT_Y, 1.0);
        Quaternion c = Quaternion.axisAngle(new Vec3(1.0, 1.0, 0.0), 2.0);
        Track<Quaternion> track = Track.builder(TrackValues.QUATERNION)
                .hermite(0.0, a)
                .hermite(4.0, b)
                .hermite(9.0, c)
                .build();
        assertEquals(0.0, track.sample(0.0).angleTo(a), 1.0e-9);
        assertEquals(0.0, track.sample(4.0).angleTo(b), 1.0e-9);
        assertEquals(0.0, track.sample(9.0).angleTo(c), 1.0e-9);
        Quaternion previous = track.sample(0.0);
        for (double t = 0.1; t <= 9.0; t += 0.1) {
            Quaternion q = track.sample(t);
            assertEquals(1.0, q.lengthSquared(), 1.0e-9);
            assertTrue(previous.angleTo(q) < 0.1, "continuous at t=" + t);
            previous = q;
        }
    }

    @Test
    void markersFireForwardAndOnlyStickyOnesOnSeek() {
        Track<Double> track = Track.scalar()
                .hermite(0.0, 0.0)
                .hermite(20.0, 1.0)
                .marker(Marker.of(5, "whoosh"))
                .marker(Marker.sticky(8, "sky_dark"))
                .marker(Marker.of(12, "impact"))
                .build();
        assertEquals(List.of("whoosh"), names(track.markersCrossed(4.5, 5.0)));
        assertTrue(track.markersCrossed(5.0, 5.9).isEmpty());
        assertTrue(track.markersCrossed(13.0, 2.0).isEmpty());
        assertEquals(List.of("whoosh", "sky_dark", "impact"), names(track.markersCrossed(0.0, 12.0)));
        assertEquals(List.of("sky_dark"), names(track.markersOnSeek(15.0)));
        assertTrue(track.markersOnSeek(7.0).isEmpty());
    }

    @Test
    void layersAddAndOverrideWithWeights() {
        Track<Double> base = Track.scalar().eased(0.0, 0.0, Easing.LINEAR).eased(10.0, 10.0, Easing.LINEAR).build();
        StatelessSampler<Double> bump = StatelessSampler.constant(2.0);
        StatelessSampler<Double> pin = StatelessSampler.constant(100.0);
        LayeredTrack<Double> layered = new LayeredTrack<>(TrackValues.SCALAR, base, List.of(
                TrackLayer.additive(bump, StatelessSampler.constant(0.5)),
                TrackLayer.override(pin, Track.scalar().stepped(0.0, 0.0).stepped(8.0, 0.25).build())
        ));
        assertEquals(5.0 + 1.0, layered.sample(5.0), EPS);
        double below = 9.0 + 1.0;
        assertEquals(below + ((100.0 - below) * 0.25), layered.sample(9.0), EPS);
    }

    @Test
    void holdTicksQuantisesOnTwos() {
        Track<Double> track = Track.scalar()
                .eased(0.0, 0.0, Easing.LINEAR)
                .eased(10.0, 10.0, Easing.LINEAR)
                .holdTicks(2)
                .build();
        assertEquals(2.0, track.sample(2.0), EPS);
        assertEquals(2.0, track.sample(3.9), EPS);
        assertEquals(4.0, track.sample(4.0), EPS);
    }

    @Test
    void duplicateKeyTimesAreRejected() {
        assertThrows(IllegalStateException.class, () -> Track.scalar().hermite(1.0, 0.0).hermite(1.0, 2.0).build());
        assertThrows(IllegalArgumentException.class,
                () -> new TrackKey<>(0.0, 1.0, 2.0, false, Interpolation.HERMITE, Tangent.auto(), Tangent.auto()));
    }

    private static List<String> names(List<Marker> markers) {
        return markers.stream().map(Marker::name).toList();
    }
}
