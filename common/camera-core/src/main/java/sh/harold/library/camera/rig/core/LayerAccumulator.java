package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.stage.LayerSample;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Sums channel contributions for one frame into a {@link LayerSample}. */
final class LayerAccumulator {

    private double pitch;
    private double pitchExempt;
    private double roll;
    private double rollExempt;
    private double fov;
    private double fovExempt;
    private double eyeHeight;
    private double eyeTarget = Double.NaN;
    private double shake;
    private double lookScale = 1.0;
    private boolean pitchUnwound;
    private final Set<CameraChannel> active = EnumSet.noneOf(CameraChannel.class);
    private final List<Key> cues = new ArrayList<>();

    void add(CameraChannel channel, double value, boolean exempt) {
        active.add(channel);
        switch (channel) {
            case PITCH -> {
                if (exempt) {
                    pitchExempt += value;
                } else {
                    pitch += value;
                }
            }
            case ROLL -> {
                if (exempt) {
                    rollExempt += value;
                } else {
                    roll += value;
                }
            }
            case FOV -> {
                if (exempt) {
                    fovExempt += value;
                } else {
                    fov += value;
                }
            }
            case EYE_HEIGHT -> eyeHeight += value;
            case SHAKE -> shake += value;
            case LOOK_SCALE -> lookScale *= Math.max(0.0, 1.0 + value);
        }
    }

    /** An absolute eye-height value; cues are sampled in start order, so the latest-started one wins. */
    void setEyeTarget(double value) {
        active.add(CameraChannel.EYE_HEIGHT);
        eyeTarget = value;
    }

    void markUnwound() {
        pitchUnwound = true;
    }

    void addCue(Key key) {
        cues.add(key);
    }

    LayerSample toSample() {
        if (active.isEmpty() && cues.isEmpty()) {
            return LayerSample.EMPTY;
        }
        return new LayerSample(pitch, pitchExempt, roll, rollExempt, fov, fovExempt, eyeHeight, eyeTarget, Math.max(0.0, shake),
                lookScale, pitchUnwound, active, cues);
    }
}
