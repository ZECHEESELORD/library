package sh.harold.library.tween.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tween.signal.SignalChannelSpec;
import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.track.Track;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatefulSamplerTest {

    @Test
    void criticallyDampedSpringNeverOvershoots() {
        Spring spring = new Spring(SpringSpec.critical(2.0), 0.0);
        spring.retarget(1.0);
        double previous = 0.0;
        for (int tick = 0; tick < 60; tick++) {
            for (int frame = 1; frame <= 4; frame++) {
                double value = spring.sampleDouble(frame / 4.0);
                assertTrue(value <= 1.0 + 1.0e-12, "no overshoot");
                assertTrue(value >= previous - 1.0e-12, "monotonic");
                previous = value;
            }
            spring.tick();
        }
        assertEquals(1.0, spring.position(), 1.0e-4);
    }

    @Test
    void underdampedSpringOvershootsByTheSpecFraction() {
        SpringSpec spec = new SpringSpec(1.5, 0.5);
        Spring spring = new Spring(spec, 0.0);
        spring.retarget(1.0);
        double peak = 0.0;
        for (int tick = 0; tick < 80; tick++) {
            for (int frame = 0; frame < 100; frame++) {
                peak = Math.max(peak, spring.sampleDouble(frame / 100.0));
            }
            spring.tick();
        }
        assertEquals(1.0 + spec.overshootFraction(), peak, 1.0e-3);
    }

    @Test
    void overdampedSpringApproachesWithoutOvershoot() {
        Spring spring = new Spring(new SpringSpec(2.0, 2.5), 5.0);
        spring.retarget(0.0);
        for (int tick = 0; tick < 200; tick++) {
            spring.tick();
            assertTrue(spring.position() >= -1.0e-12);
        }
        assertEquals(0.0, spring.position(), 1.0e-3);
    }

    @Test
    void perFrameSamplesAreFrameRateIndependent() {
        SpringSpec spec = new SpringSpec(2.2, 0.8);
        Spring spring = new Spring(spec, 0.0);
        spring.kickVelocity(0.3);
        spring.retarget(2.0);
        for (int tick = 0; tick < 7; tick++) {
            spring.tick();
        }
        // After 7 ticks, a frame at partial 0.37 equals the closed form from the start at 7.37 ticks.
        double expected = Spring.advance(spec, 0.0, 0.3, 2.0, 7.37)[0];
        assertEquals(expected, spring.sampleDouble(0.37), 1.0e-9);
    }

    @Test
    void retargetKeepsVelocityAndResetIsACut() {
        Spring spring = new Spring(SpringSpec.fromOvershoot(10.0, 10.0), 0.0);
        spring.retarget(10.0);
        spring.tick();
        spring.tick();
        double velocity = spring.velocity();
        assertTrue(velocity > 0.0);
        spring.retarget(-10.0);
        assertEquals(velocity, spring.velocity(), 0.0);

        spring.reset(4.0);
        assertEquals(4.0, spring.sampleDouble(0.0), 0.0);
        assertEquals(4.0, spring.sampleDouble(0.5), 0.0);
        spring.tick();
        assertEquals(4.0, spring.position(), 0.0);
    }

    @Test
    void impulseKicksDisturbAndReturn() {
        Spring spring = new Spring(new SpringSpec(3.0, 0.6), 0.0);
        spring.kickVelocity(-0.5);
        spring.tick();
        assertTrue(spring.position() < 0.0, "landing dip");
        for (int tick = 0; tick < 60; tick++) {
            spring.tick();
        }
        assertEquals(0.0, spring.position(), 1.0e-4);

        Vec3Spring recoil = new Vec3Spring(SpringSpec.critical(4.0), Vec3.ZERO);
        recoil.kickDisplacement(new Vec3(0.0, 0.0, -1.0));
        assertEquals(-1.0, recoil.sample(0.0).z(), 0.0);
        for (int tick = 0; tick < 40; tick++) {
            recoil.tick();
        }
        assertEquals(0.0, recoil.position().z(), 1.0e-4);
    }

    @Test
    void signalChannelFollowsTheMappedSignal() {
        Track<Double> speedToFov = Track.scalar().hermite(0.2, 0.0).hermite(0.4, 8.0).build();
        double[] speed = {0.0};
        SignalChannel fov = new SignalChannel(new SignalChannelSpec(speedToFov, SpringSpec.critical(3.0)),
                () -> speed[0], 0.0);
        speed[0] = 0.4;
        for (int tick = 0; tick < 40; tick++) {
            fov.tick();
        }
        assertEquals(8.0, fov.value(), 1.0e-3);
        speed[0] = 0.1;
        for (int tick = 0; tick < 40; tick++) {
            fov.tick();
        }
        assertEquals(0.0, fov.value(), 1.0e-3);
    }

    @Test
    void seekReSimulatesDeterministically() {
        SpringSpec spec = new SpringSpec(2.0, 0.45);
        SeekableSampler.TickDriver<Spring> driver = (spring, tick) -> {
            spring.retarget(tick < 10 ? 0.0 : 5.0);
            if (tick == 4) {
                spring.kickVelocity(1.5);
            }
            if (tick == 20) {
                spring.reset(-2.0);
            }
        };
        SeekableSampler<Spring, Double> forward = new SeekableSampler<>(() -> new Spring(spec, 0.0), driver);
        List<Double> times = new ArrayList<>();
        List<Double> played = new ArrayList<>();
        for (int frame = 0; frame <= 30 * 3; frame++) {
            double t = frame / 3.0;
            times.add(t);
            played.add(forward.sample(t));
        }
        // Scrub backward on the same instance, then fresh instances per frame: identical values.
        for (int i = times.size() - 1; i >= 0; i--) {
            assertEquals(played.get(i), forward.sample(times.get(i)), 0.0, "backward t=" + times.get(i));
        }
        for (int i = 0; i < times.size(); i += 7) {
            SeekableSampler<Spring, Double> fresh = new SeekableSampler<>(() -> new Spring(spec, 0.0), driver);
            assertEquals(played.get(i), fresh.sample(times.get(i)), 0.0, "fresh t=" + times.get(i));
        }
        // The reset at tick 20 is a cut: the tick-21 state starts from rest at -2 toward 5.
        assertTrue(forward.sample(21.0) < 0.0);
    }
}
