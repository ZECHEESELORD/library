package sh.harold.library.npc.behavior.core;

import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcPersonalityTuning;
import sh.harold.library.npc.behavior.NpcTimingBand;

import java.util.Objects;

/** Vanilla-safe personality timing and gaze composition. */
public final class NpcPersonalityMotion {

    public static final float MAXIMUM_HEAD_YAW_FROM_BODY = 55.0f;
    public static final float BODY_FOLLOW_THRESHOLD = 35.0f;
    public static final float MAXIMUM_UP_PITCH = -25.0f;
    public static final float MAXIMUM_DOWN_PITCH = 35.0f;

    private NpcPersonalityMotion() {
    }

    public static Signature signature(NpcPersonalityPreset preset) {
        return switch (Objects.requireNonNull(preset, "preset")) {
            case NEUTRAL -> new Signature(5.0f, 2.4f, 3.5f, 2, 0.8f, 0.0f, 0.0f);
            case WARM -> new Signature(8.0f, 3.4f, 5.5f, 0, 1.4f, 0.0f, 2.0f);
            case CONFIDENT -> new Signature(6.0f, 4.5f, 4.5f, 0, 0.2f, 0.0f, 0.0f);
            case CURIOUS -> new Signature(8.5f, 2.8f, 6.0f, 1, 1.8f, 8.0f, -2.0f);
            case CONFUSED -> new Signature(7.5f, 2.0f, 6.5f, 5, 4.5f, 11.0f, 0.0f);
            case NERVOUS -> new Signature(10.0f, 1.8f, 8.0f, 4, 3.6f, 3.5f, 1.0f);
            case DISTRACTED -> new Signature(8.0f, 2.7f, 5.5f, 8, 1.8f, 0.0f, 0.0f);
            case SLEEPY -> new Signature(2.6f, 1.3f, 2.2f, 6, 0.4f, 0.0f, 8.0f);
        };
    }

    /** Randomizes and personality-scales one named timing band. */
    public static int timingTicks(
            NpcTimingBand band,
            NpcPersonalityTuning tuning,
            NpcBehaviorRandom random
    ) {
        Objects.requireNonNull(band, "band");
        Objects.requireNonNull(tuning, "tuning");
        Objects.requireNonNull(random, "random");
        int base = random.betweenInclusive(band.minimumTicks(), band.maximumTicks());
        return Math.max(1, (int) Math.round(base / tuning.tempoMultiplier()));
    }

    public record Signature(
            float headDegreesPerTick,
            float bodyDegreesPerTick,
            float pitchDegreesPerTick,
            int bodyFollowDelayTicks,
            float naturalMicroGlanceDegrees,
            float acquisitionOvershootDegrees,
            float pitchBiasDegrees
    ) {
        public Signature {
            if (headDegreesPerTick <= 0.0f || bodyDegreesPerTick <= 0.0f || pitchDegreesPerTick <= 0.0f) {
                throw new IllegalArgumentException("motion speeds must be positive");
            }
            if (bodyFollowDelayTicks < 0) {
                throw new IllegalArgumentException("bodyFollowDelayTicks must not be negative");
            }
        }

        public Signature tuned(NpcPersonalityTuning tuning) {
            float tempo = (float) tuning.tempoMultiplier();
            float intensity = (float) tuning.intensityMultiplier();
            return new Signature(
                    headDegreesPerTick * tempo,
                    bodyDegreesPerTick * tempo,
                    pitchDegreesPerTick * tempo,
                    Math.max(0, (int) Math.round(bodyFollowDelayTicks / tuning.tempoMultiplier())),
                    naturalMicroGlanceDegrees * intensity,
                    acquisitionOvershootDegrees * intensity,
                    pitchBiasDegrees * intensity
            );
        }
    }
}
