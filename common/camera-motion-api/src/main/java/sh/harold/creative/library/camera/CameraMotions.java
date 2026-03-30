package sh.harold.creative.library.camera;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public final class CameraMotions {

    private CameraMotions() {
    }

    public static CameraMotion motion(Key key, BlendMode blendMode, CameraAxis yaw, CameraAxis pitch, Envelope envelope) {
        return new CameraMotion(Objects.requireNonNull(key, "key"), blendMode, yaw, pitch, envelope);
    }

    public static CameraAxis axis(double amplitudeDegrees, long periodTicks, long phaseTicks, Waveform waveform) {
        return axis(amplitudeDegrees, periodTicks, phaseTicks, waveform, 0L);
    }

    public static CameraAxis axis(double amplitudeDegrees, long periodTicks, long phaseTicks, Waveform waveform, long seed) {
        return new CameraAxis(amplitudeDegrees, periodTicks, phaseTicks, waveform, seed);
    }

    public static CameraAxis none() {
        return new CameraAxis(0.0, 1L, 0L, Waveform.SINE, 0L);
    }

    public static Envelope.Constant constant(long durationTicks, double strength) {
        return new Envelope.Constant(durationTicks, strength);
    }

    public static Envelope.LinearDecay linearDecay(long durationTicks, double startStrength, double endStrength) {
        return new Envelope.LinearDecay(durationTicks, startStrength, endStrength);
    }

    public static Envelope.ExponentialDecay exponentialDecay(long durationTicks, double startStrength, double lambda) {
        return new Envelope.ExponentialDecay(durationTicks, startStrength, lambda);
    }

    public static Envelope.ExponentialHalfLife exponentialHalfLife(long durationTicks, double startStrength, long halfLifeTicks) {
        return new Envelope.ExponentialHalfLife(durationTicks, startStrength, halfLifeTicks);
    }

    public static Envelope.AttackHoldRelease attackHoldRelease(long attackTicks, long holdTicks, long releaseTicks, double peakStrength) {
        return new Envelope.AttackHoldRelease(attackTicks, holdTicks, releaseTicks, peakStrength);
    }

    public static Envelope.EaseOut easeOut(long durationTicks, double startStrength, EaseOutCurve curve) {
        return new Envelope.EaseOut(durationTicks, startStrength, curve);
    }
}
