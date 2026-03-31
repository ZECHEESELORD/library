package sh.harold.creative.library.tween;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TweenApiTest {

    @Test
    void easingFunctionsStayInUsefulRange() {
        assertEquals(0.0, Easing.LINEAR.apply(0.0), 1.0e-9);
        assertEquals(1.0, Easing.LINEAR.apply(1.0), 1.0e-9);
        assertEquals(0.5, Easing.SMOOTHSTEP.apply(0.5), 1.0e-9);
    }

    @Test
    void attackHoldReleaseEnvelopePhasesCorrectly() {
        Envelope.AttackHoldRelease envelope = new Envelope.AttackHoldRelease(2L, 2L, 2L, 1.0);

        assertEquals(0.5, envelope.sample(0L, 6L), 1.0e-9);
        assertEquals(1.0, envelope.sample(2L, 6L), 1.0e-9);
        assertEquals(0.5, envelope.sample(5L, 6L), 1.0e-9);
    }
}
