package sh.harold.creative.library.tick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickMathTest {

    @Test
    void localAndActiveTickClampAtZero() {
        assertEquals(0L, TickMath.localTick(5L, 8L));
        assertEquals(0L, TickMath.activeTick(5L, 8L, 4L));
        assertEquals(2L, TickMath.activeTick(10L, 5L, 3L));
    }

    @Test
    void zeroDurationIsImmediateCompletion() {
        assertEquals(1.0, TickMath.progress(0L, 0L));
        assertEquals(1.0, TickMath.progress(12L, 0L));
    }

    @Test
    void progressClampsToUnitInterval() {
        assertEquals(0.0, TickMath.progress(0L, 5L));
        assertEquals(0.4, TickMath.progress(2L, 5L), 1.0e-9);
        assertEquals(1.0, TickMath.progress(7L, 5L));
    }
}
