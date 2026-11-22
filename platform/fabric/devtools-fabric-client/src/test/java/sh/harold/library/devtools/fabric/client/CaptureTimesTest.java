package sh.harold.library.devtools.fabric.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureTimesTest {

    @Test
    void sixtyFpsShowsThirdsOfATick() {
        assertEquals(0, CaptureTimes.wholeTicks(0, 60));
        assertEquals(0.0f, CaptureTimes.partialTick(0, 60));
        assertEquals(1.0f / 3.0f, CaptureTimes.partialTick(1, 60), 1e-7);
        assertEquals(2.0f / 3.0f, CaptureTimes.partialTick(2, 60), 1e-7);
        assertEquals(1, CaptureTimes.wholeTicks(3, 60));
        assertEquals(0.0f, CaptureTimes.partialTick(3, 60));
        assertEquals(1.0f / 3.0f, CaptureTimes.deltaTicks(60), 1e-7);
    }

    @Test
    void ticksBeforeSumToWholeTicksWithNoDrift() {
        for (int fps : new int[] {24, 30, 50, 60, 144, 7}) {
            long ran = 0;
            for (long k = 0; k <= 10_000; k++) {
                ran += CaptureTimes.ticksBefore(k, fps);
                assertEquals(CaptureTimes.wholeTicks(k, fps), ran, "fps " + fps + " frame " + k);
                float partial = CaptureTimes.partialTick(k, fps);
                assertTrue(partial >= 0.0f && partial < 1.0f);
            }
            // After exactly fps frames, 20 ticks have run and the partial is back to zero.
            assertEquals(20, CaptureTimes.wholeTicks(fps, fps));
            assertEquals(0.0f, CaptureTimes.partialTick(fps, fps));
        }
    }

    @Test
    void slowFrameRatesRunSeveralTicksPerFrame() {
        assertEquals(0, CaptureTimes.ticksBefore(0, 10));
        assertEquals(2, CaptureTimes.ticksBefore(1, 10));
        assertEquals(2.0f, CaptureTimes.deltaTicks(10));
    }

    @Test
    void timeIsWholePlusPartial() {
        assertEquals(10.0 + 2.0f / 3.0f, CaptureTimes.time(32, 60), 1e-6);
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> CaptureTimes.wholeTicks(-1, 60));
        assertThrows(IllegalArgumentException.class, () -> CaptureTimes.partialTick(0, 0));
        assertThrows(IllegalArgumentException.class, () -> CaptureTimes.deltaTicks(-5));
    }
}
