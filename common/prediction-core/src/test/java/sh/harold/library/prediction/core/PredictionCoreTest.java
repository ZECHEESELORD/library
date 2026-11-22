package sh.harold.library.prediction.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.prediction.Correction;
import sh.harold.library.prediction.DeterministicRandom;
import sh.harold.library.prediction.ErrorSmoothingSpec;
import sh.harold.library.prediction.PredictionModel;
import sh.harold.library.prediction.ReplayResult;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tween.spring.SpringSpec;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictionCoreTest {

    /** A toy body: input is a push per tick (0 = none); the state carries position, velocity and a move flag. */
    record Body(double x, double v, boolean dashing) {
    }

    record Push(double accel, boolean dash) {
    }

    private static final PredictionModel<Push, Body> MODEL = (state, input, tick) -> {
        boolean dashing = state.dashing() || input.dash();
        double v = (state.v() + input.accel() + (dashing ? 0.5 : 0.0)) * 0.9;
        return new Body(state.x() + v, v, dashing);
    };

    @Test
    void ringKeepsTheLast64TicksAndRewritesFromAnOlderTick() {
        HistoryRing<Integer> ring = new HistoryRing<>();
        for (int tick = 100; tick < 300; tick++) {
            ring.put(tick, tick * 2);
        }
        assertEquals(64, ring.size());
        assertEquals(236, ring.oldestTick());
        assertEquals(299, ring.newestTick());
        assertTrue(ring.get(235).isEmpty());
        assertEquals(472, ring.get(236).orElseThrow());
        ring.put(250, -1);
        assertEquals(250, ring.newestTick());
        assertTrue(ring.get(251).isEmpty());
        assertEquals(-1, ring.get(250).orElseThrow());
        ring.set(240, 7);
        assertEquals(7, ring.get(240).orElseThrow());
        assertEquals(250, ring.newestTick());
        assertThrows(IllegalArgumentException.class, () -> ring.put(260, 0));
    }

    @Test
    void correctionRewindsAndReplaysTheStoredInputs() {
        PredictionHistory<Push, Body> history = new PredictionHistory<>(MODEL, 0L, new Body(0.0, 0.0, false));
        for (long tick = 1; tick <= 20; tick++) {
            history.advance(tick, new Push(tick % 3 == 0 ? 0.2 : 0.0, false));
        }
        Body predicted = history.current();

        // The server says at tick 8 the body was 1 block further on.
        Body serverAt8 = new Body(history.stateAt(8).orElseThrow().x() + 1.0, history.stateAt(8).orElseThrow().v(), false);
        ReplayResult<Body> result = history.reconcile(new Correction<>(8L, serverAt8));

        assertEquals(ReplayResult.Outcome.REPLAYED, result.outcome());
        assertEquals(12L, result.replayedTicks());
        assertEquals(predicted, result.predictedBefore());

        // Straight simulation from the corrected state gives the same answer.
        Body expected = serverAt8;
        for (long tick = 9; tick <= 20; tick++) {
            expected = MODEL.step(expected, new Push(tick % 3 == 0 ? 0.2 : 0.0, false), tick);
        }
        assertEquals(expected, history.current());
        assertEquals(expected, result.current());
        assertEquals(20L, history.currentTick());
    }

    @Test
    void rejectedMoveReplaysAsIfItNeverStarted() {
        PredictionHistory<Push, Body> history = new PredictionHistory<>(MODEL, 0L, new Body(0.0, 0.0, false));
        for (long tick = 1; tick <= 10; tick++) {
            history.advance(tick, new Push(0.1, tick == 4));
        }
        assertTrue(history.current().dashing());
        Body serverAt3 = history.stateAt(3).orElseThrow();
        // Reject the dash claimed at tick 4: correct to the tick-3 state with the dash edge removed from replay.
        Body nudged = new Body(serverAt3.x(), serverAt3.v() + 1.0e-9, false);
        ReplayResult<Body> result = history.reconcile(new Correction<>(3L, nudged),
                (tick, input) -> new Push(input.accel(), false));
        assertTrue(result.changed());
        assertFalse(history.current().dashing());
        assertFalse(history.inputAt(4).orElseThrow().dash());
    }

    @Test
    void confirmingTooOldAndFutureCorrectionsChangeNothing() {
        PredictionHistory<Push, Body> history = new PredictionHistory<>(MODEL, 0L, new Body(0.0, 0.0, false));
        for (long tick = 1; tick <= 100; tick++) {
            history.advance(tick, new Push(0.05, false));
        }
        Body current = history.current();
        assertEquals(ReplayResult.Outcome.CONFIRMED,
                history.reconcile(new Correction<>(90L, history.stateAt(90).orElseThrow())).outcome());
        assertEquals(ReplayResult.Outcome.TOO_OLD,
                history.reconcile(new Correction<>(10L, new Body(5, 0, false))).outcome());
        assertEquals(ReplayResult.Outcome.FUTURE,
                history.reconcile(new Correction<>(101L, new Body(5, 0, false))).outcome());
        assertEquals(current, history.current());
        assertThrows(IllegalArgumentException.class, () -> history.advance(103L, new Push(0, false)));
    }

    @Test
    void errorSmootherClosesTheGapWithoutOvershootOrPop() {
        ErrorSmoother smoother = new ErrorSmoother(ErrorSmoothingSpec.DEFAULT);
        Vec3 before = new Vec3(10.0, 64.0, 10.0);
        Vec3 corrected = new Vec3(10.4, 64.0, 10.0);
        assertTrue(smoother.absorb(before, corrected));
        // The first drawn frame is exactly where the body was drawn before the correction: no pop.
        assertEquals(before, smoother.drawn(corrected, 0.0));
        double previousError = 0.4;
        for (int tick = 0; tick < 4; tick++) {
            for (int frame = 1; frame <= 3; frame++) {
                double error = smoother.offset(frame / 3.0).length();
                assertTrue(error <= previousError + 1.0e-12, "closes monotonically");
                previousError = error;
            }
            smoother.tick();
        }
        assertTrue(smoother.offset(0.0).length() < 0.4 * 0.02, "under 2% after 4 ticks");
        assertTrue(smoother.offset(0.0).x() <= 0.0, "never overshoots past the corrected value");
    }

    @Test
    void errorSmootherSnapsHugeErrors() {
        ErrorSmoother smoother = new ErrorSmoother(new ErrorSmoothingSpec(SpringSpec.critical(9.0), 3.0));
        assertFalse(smoother.absorb(Vec3.ZERO, new Vec3(5.0, 0.0, 0.0)));
        assertEquals(Vec3.ZERO, smoother.offset(0.0));
    }

    @Test
    void streamsAreDeterministicAndKeyed() {
        DeterministicRandom a = SeededStreams.open(4L, 7L, "d20");
        DeterministicRandom b = SeededStreams.open(4L, 7L, "d20");
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        assertNotEquals(SeededStreams.open(4L, 7L, "d20").nextLong(), SeededStreams.open(4L, 8L, "d20").nextLong());
        assertNotEquals(SeededStreams.open(4L, 7L, "d20").nextLong(), SeededStreams.open(5L, 7L, "d20").nextLong());
        assertNotEquals(SeededStreams.open(4L, 7L, "d20").nextLong(), SeededStreams.open(4L, 7L, "damage").nextLong());
    }

    @Test
    void splitDependsOnlyOnTheKey() {
        DeterministicRandom parent = SeededStreams.open(4L, 1L, "strike");
        long childFirst = parent.split("crit").nextLong();
        parent.nextLong();
        parent.nextLong();
        assertEquals(childFirst, parent.split("crit").nextLong());
        assertEquals(childFirst, SeededStreams.open(4L, 1L, "strike/crit").nextLong());
        assertNotEquals(childFirst, parent.split("miss").nextLong());
    }

    @Test
    void diceAreUniformAndInRange() {
        DeterministicRandom dice = SeededStreams.open(99L, 0L, "d20");
        int[] counts = new int[21];
        for (int i = 0; i < 200_000; i++) {
            int face = dice.roll(20);
            assertTrue(face >= 1 && face <= 20);
            counts[face]++;
        }
        for (int face = 1; face <= 20; face++) {
            assertEquals(10_000, counts[face], 400, "face " + face);
        }
        Set<Double> doubles = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            double d = dice.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0);
            doubles.add(d);
        }
        assertEquals(1000, doubles.size());
    }

    @Test
    void mixerIsSplitMix64() {
        // Known SplitMix64 outputs for seed 0.
        assertEquals(0xE220A8397B1DCDAFL, SeededStreams.finalise(SeededStreams.GOLDEN_GAMMA));
        assertEquals(0x6E789E6AA1B965F4L, SeededStreams.finalise(SeededStreams.GOLDEN_GAMMA * 2));
        assertEquals(0xCBF29CE484222325L, SeededStreams.fnv1a64(""));
        assertEquals(0xAF63DC4C8601EC8CL, SeededStreams.fnv1a64("a"));
    }
}
