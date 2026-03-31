package sh.harold.creative.library.tween.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tween.Easing;
import sh.harold.creative.library.tween.Envelope;
import sh.harold.creative.library.tween.HoldBehavior;
import sh.harold.creative.library.tween.Interpolators;
import sh.harold.creative.library.tween.RepeatMode;
import sh.harold.creative.library.tween.RepeatSpec;
import sh.harold.creative.library.tween.Tween;
import sh.harold.creative.library.tween.TweenHandle;
import sh.harold.creative.library.tween.TweenSample;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTweenControllerTest {

    @Test
    void sameKeyReplacementInvalidatesStaleHandle() {
        StandardTweenController<Double> controller = new StandardTweenController<>();

        TweenHandle first = controller.start(tween("fade", 0.0, 1.0, 0L, 4L));
        TweenHandle second = controller.start(tween("fade", 1.0, 2.0, 0L, 4L));

        assertFalse(first.active());
        assertTrue(second.active());
    }

    @Test
    void refreshPreservesHandleAndRestartsTween() {
        StandardTweenController<Double> controller = new StandardTweenController<>();
        TweenHandle handle = controller.start(tween("fade", 0.0, 10.0, 0L, 4L));

        controller.tick();
        controller.start(new Tween<>(
                Key.key("test", "fade"),
                10.0,
                20.0,
                Interpolators.doubles(),
                0L,
                4L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REFRESH
        ));

        assertTrue(handle.active());
        assertEquals(10.0, controller.tick().getFirst().value(), 1.0e-9);
    }

    @Test
    void delayCanSuppressOrEmitFromValue() {
        StandardTweenController<Double> controller = new StandardTweenController<>();
        controller.start(new Tween<>(
                Key.key("test", "delay"),
                2.0,
                6.0,
                Interpolators.doubles(),
                2L,
                4L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.EMIT_FROM_VALUE,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ));

        List<TweenSample<Double>> first = controller.tick();
        assertEquals(1, first.size());
        assertEquals(2.0, first.getFirst().value(), 1.0e-9);
        assertEquals(0.0, first.getFirst().strength(), 1.0e-9);
    }

    @Test
    void zeroDurationCompletesImmediately() {
        StandardTweenController<Double> controller = new StandardTweenController<>();
        controller.start(tween("snap", 0.0, 10.0, 0L, 0L));

        TweenSample<Double> sample = controller.tick().getFirst();
        assertEquals(10.0, sample.value(), 1.0e-9);
        assertTrue(sample.complete());
        assertFalse(controller.hasActiveTweens());
    }

    @Test
    void pingPongReversesAcrossCycles() {
        StandardTweenController<Double> controller = new StandardTweenController<>();
        controller.start(new Tween<>(
                Key.key("test", "ping"),
                0.0,
                10.0,
                Interpolators.doubles(),
                0L,
                2L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                new RepeatSpec(RepeatMode.PING_PONG, 1),
                InstanceConflictPolicy.REPLACE
        ));

        assertEquals(0.0, controller.tick().getFirst().value(), 1.0e-9);
        assertEquals(5.0, controller.tick().getFirst().value(), 1.0e-9);
        assertEquals(10.0, controller.tick().getFirst().value(), 1.0e-9);
        assertEquals(5.0, controller.tick().getFirst().value(), 1.0e-9);
        assertEquals(0.0, controller.tick().getFirst().value(), 1.0e-9);
        assertFalse(controller.hasActiveTweens());
    }

    @Test
    void pauseAndResumeFreezeProgress() {
        StandardTweenController<Double> controller = new StandardTweenController<>();
        TweenHandle handle = controller.start(tween("pause", 0.0, 10.0, 0L, 4L));

        assertEquals(0.0, controller.tick().getFirst().value(), 1.0e-9);
        handle.pause();
        assertEquals(0.0, controller.tick().getFirst().value(), 1.0e-9);
        assertEquals(0.0, controller.tick().getFirst().value(), 1.0e-9);
        handle.resume();
        assertEquals(2.5, controller.tick().getFirst().value(), 1.0e-9);
    }

    private static Tween<Double> tween(String key, double from, double to, long delayTicks, long durationTicks) {
        return new Tween<>(
                Key.key("test", key),
                from,
                to,
                Interpolators.doubles(),
                delayTicks,
                durationTicks,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        );
    }
}
