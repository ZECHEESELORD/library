package sh.harold.library.tween;

import org.junit.jupiter.api.Test;
import sh.harold.library.tween.generator.Generators;
import sh.harold.library.tween.noise.NoiseOctave;
import sh.harold.library.tween.noise.NoiseSpec;
import sh.harold.library.tween.noise.SmoothNoise;
import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.track.Track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationApiTest {

    @Test
    void newEasingsHitTheirEndpointsAndOvershootWhereExpected() {
        for (Easing easing : new Easing[] {Easing.BACK_OUT, Easing.BACK_IN_OUT, Easing.ELASTIC_OUT, Easing.BOUNCE_OUT}) {
            assertEquals(0.0, easing.apply(0.0), 1.0e-9, easing.name());
            assertEquals(1.0, easing.apply(1.0), 1.0e-9, easing.name());
        }
        double peak = 0.0;
        for (double u = 0.0; u <= 1.0; u += 0.01) {
            peak = Math.max(peak, Easing.BACK_OUT.apply(u));
        }
        assertTrue(peak > 1.09 && peak < 1.11, "BACK_OUT peaks about 10% past the target, got " + peak);
        assertEquals(Easing.BOUNCE.apply(0.3), Easing.BOUNCE_OUT.apply(0.3), 0.0);
        assertEquals(0.5, Easing.BACK_IN_OUT.apply(0.5), 1.0e-9);
    }

    @Test
    void cubicBezierSolvesForTime() {
        EasingFunction linear = Easings.cubicBezier(0.0, 0.0, 1.0, 1.0);
        assertEquals(0.3, linear.apply(0.3), 1.0e-7);
        EasingFunction cssEase = Easings.cubicBezier(0.25, 0.1, 0.25, 1.0);
        assertEquals(0.8024, cssEase.apply(0.5), 1.0e-3);
        EasingFunction overshoot = Easings.cubicBezier(0.3, 1.6, 0.6, 1.0);
        assertTrue(overshoot.apply(0.6) > 1.0);
        assertEquals(1.0, overshoot.apply(1.0), 0.0);
    }

    @Test
    void springSpecFromOvershootMatchesCatalogTable() {
        assertEquals(0.5, SpringSpec.fromOvershoot(16.3, 20.0).dampingRatio(), 0.01);
        assertEquals(0.7, SpringSpec.fromOvershoot(4.6, 20.0).dampingRatio(), 0.01);
        assertEquals(0.35, SpringSpec.fromOvershoot(30.9, 20.0).dampingRatio(), 0.01);
        SpringSpec none = SpringSpec.fromOvershoot(0.0, 12.0);
        assertEquals(1.0, none.dampingRatio(), 0.0);
        assertEquals(12.0, none.settleTicks(), 1.0e-9);
        assertEquals(0.163, new SpringSpec(3.0, 0.5).overshootFraction(), 0.001);
    }

    @Test
    void noiseIsSeededSmoothAndBounded() {
        NoiseSpec spec = NoiseSpec.of(42L, new NoiseOctave(2.0, 1.0), new NoiseOctave(7.0, 0.25));
        NoiseSpec other = NoiseSpec.of(43L, new NoiseOctave(2.0, 1.0), new NoiseOctave(7.0, 0.25));
        double maxStep = 0.0;
        boolean differs = false;
        boolean axesDiffer = false;
        double previous = SmoothNoise.sample(spec, 0.0);
        for (int i = 1; i <= 4000; i++) {
            double seconds = i * 0.001;
            double value = SmoothNoise.sample(spec, seconds);
            assertEquals(value, SmoothNoise.sample(spec, seconds), 0.0);
            assertTrue(Math.abs(value) <= spec.peakAmplitude());
            maxStep = Math.max(maxStep, Math.abs(value - previous));
            differs |= value != SmoothNoise.sample(other, seconds);
            axesDiffer |= value != SmoothNoise.sample(spec, 1, seconds);
            previous = value;
        }
        assertTrue(maxStep < 0.05, "continuous, max step " + maxStep);
        assertTrue(differs);
        assertTrue(axesDiffer);
        assertEquals(SmoothNoise.sample(spec, 0.25), SmoothNoise.sampleTicks(spec, 0, 5.0), 0.0);
        assertNotEquals(0.0, SmoothNoise.sample(spec, 0.123));
    }

    @Test
    void overshootGeneratorPeaksPastTargetAndSettles() {
        Track<Double> track = Generators.overshoot(0.0, 10.0, 4.0, 6.0, 12.0, 8.0);
        assertEquals(0.0, track.sample(4.0), 1.0e-9);
        assertEquals(11.2, track.sample(10.0), 1.0e-9);
        assertEquals(10.0, track.sample(18.0), 1.0e-9);
        double max = 0.0;
        for (double t = 4.0; t <= 18.0; t += 0.05) {
            max = Math.max(max, track.sample(t));
        }
        assertEquals(11.2, max, 1.0e-6);
    }

    @Test
    void anticipateWindsBackBeforeTheAction() {
        Track<Double> track = Generators.anticipate(
                sh.harold.library.tween.track.TrackValues.SCALAR, 0.0, 10.0, 0.0,
                0.1, 3.0, Easing.QUAD_OUT, 5.0, Easing.CUBIC_OUT);
        assertEquals(-1.0, track.sample(3.0), 1.0e-9);
        assertEquals(10.0, track.sample(8.0), 1.0e-9);
        assertTrue(track.sample(1.5) < 0.0);
    }

    @Test
    void squashKeepsVolume() {
        Generators.AxisScale scale = Generators.squashStretch(0.75, 1.0);
        assertEquals(0.75, scale.along(), 0.0);
        assertEquals(1.1547, scale.across(), 1.0e-4);
        assertEquals(1.0, scale.along() * scale.across() * scale.across(), 1.0e-9);
    }
}
