package sh.harold.library.camera.rig.core;

import sh.harold.library.camera.rig.CameraRigSettings;
import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.LayerState;
import sh.harold.library.camera.rig.ReducedRules;
import sh.harold.library.camera.rig.SafetyLimits;
import sh.harold.library.camera.rig.stage.ConstraintStage;
import sh.harold.library.camera.rig.stage.LayerSample;
import sh.harold.library.camera.rig.stage.RigFrame;
import sh.harold.library.tween.noise.NoiseSpec;
import sh.harold.library.tween.noise.SmoothNoise;

import java.util.Objects;

/**
 * Stage 7 for wave 1: the Reduced profile and the safety limits. Limits bound only what the layers add; the shot's own
 * FOV and pitch are never clamped, so an idle rig is exactly the anchor. It also turns the capped shake amplitude into
 * this frame's pitch and roll offsets from the seeded shake noise.
 */
public final class IntensityConstraints implements ConstraintStage {

    private final ReducedRules reduced;
    private final SafetyLimits limits;
    private final NoiseSpec shakeNoise;
    private final double shakePeak;

    public IntensityConstraints(CameraRigSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.reduced = settings.reduced();
        this.limits = settings.limits();
        this.shakeNoise = settings.shakeNoise();
        this.shakePeak = shakeNoise.peakAmplitude();
    }

    @Override
    public LayerState constrainLayers(LayerSample sample, CameraState shot, RigFrame frame) {
        if (sample == LayerSample.EMPTY) {
            return LayerState.IDENTITY;
        }
        boolean isReduced = frame.reduced();
        double fov = (isReduced ? sample.fov() * reduced.fovScale() : sample.fov()) + sample.fovExempt();
        double roll = (isReduced ? sample.roll() * reduced.rollScale() : sample.roll()) + sample.rollExempt();
        double pitch = sample.pitch() + sample.pitchExempt();
        double shake = sample.shake();
        if (isReduced) {
            shake = Math.min(shake, reduced.shakeCapDegrees());
        }
        shake = clamp(shake, 0.0, limits.maxShake());

        fov = clamp(fov, -limits.maxFovAdd(), limits.maxFovAdd());
        double baseFov = shot.fovDegrees();
        fov = clampAdded(baseFov, fov, limits.fovMin(), limits.fovMax());
        if (isReduced) {
            fov = clampAdded(baseFov, fov, reduced.fovMin(), reduced.fovMax());
        }
        roll = clamp(roll, -limits.maxRoll(), limits.maxRoll());
        if (!sample.pitchUnwound()) {
            pitch = clampAdded(shot.angles().pitch(), pitch, -90.0, 90.0);
        }
        double eyeHeight = clamp(sample.eyeHeight(), limits.eyeHeightMin(), limits.eyeHeightMax());
        double lookScale = clamp(sample.lookScale(), limits.lookScaleMin(), limits.lookScaleMax());

        double shakePitch = 0.0;
        double shakeRoll = 0.0;
        if (shake > 0.0) {
            double ticks = frame.time();
            shakePitch = shake * SmoothNoise.sampleTicks(shakeNoise, 0, ticks) / shakePeak;
            boolean rollAllowed = !(isReduced && reduced.rollScale() == 0.0);
            if (rollAllowed) {
                shakeRoll = shake * SmoothNoise.sampleTicks(shakeNoise, 1, ticks) / shakePeak;
            }
        }
        return new LayerState(pitch, roll, fov, eyeHeight, shake, shakePitch, shakeRoll, lookScale,
                sample.pitchUnwound(), sample.activeChannels(), sample.activeCues());
    }

    @Override
    public CameraState constrainState(CameraState state, RigFrame frame) {
        return state;
    }

    /**
     * {@code added} limited so {@code base + added} stays in {@code [min, max]}, widened to include {@code base}: the
     * base itself is never moved. Unclamped offsets come back unchanged, bit for bit.
     */
    static double clampAdded(double base, double added, double min, double max) {
        if (added == 0.0) {
            return 0.0;
        }
        double low = Math.min(base, min);
        double high = Math.max(base, max);
        double total = base + added;
        if (total >= low && total <= high) {
            return added;
        }
        return clamp(total, low, high) - base;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
