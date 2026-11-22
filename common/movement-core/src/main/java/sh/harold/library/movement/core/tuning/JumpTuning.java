package sh.harold.library.movement.core.tuning;

import sh.harold.library.movement.JumpPreset;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

import java.util.List;

/** {@code jump.json}: 02 section 1. Presets A and C share one code path; B adds the squat. */
public record JumpTuning(JumpPreset preset, Boost boost, Charge charge, Cut cut, Camera camera, Sound sound) {

    public static final String FILE = "jump.json";

    public JumpTuning withPreset(JumpPreset next) {
        return new JumpTuning(next, boost, charge, cut, camera, sound);
    }

    public static JumpTuning decode(JsonValue root) {
        JsonObject object = root.asObject();
        JsonObject boost = Json.object(object, "boost");
        JsonObject charge = Json.object(object, "charge");
        JsonObject camera = Json.object(object, "camera");
        JsonObject sound = Json.object(object, "sound");
        JumpPreset preset;
        try {
            preset = JumpPreset.parse(Json.string(object, "preset"));
        } catch (IllegalArgumentException exception) {
            throw new DataDecodeException(object.get("preset"), "preset must be A, B or C");
        }
        List<Double> launch = Json.numbers(charge, "launchSpeeds");
        int maxTicks = Json.ticks(charge, "maxTicks");
        if (launch.size() != maxTicks + 1) {
            throw new DataDecodeException(charge.get("launchSpeeds"),
                    "launchSpeeds needs one entry per squat tick from 0 to maxTicks (" + (maxTicks + 1) + ")");
        }
        return new JumpTuning(
                preset,
                new Boost(Json.ticks(boost, "freeTicks"), Json.ticks(boost, "sustainToTick"),
                        Json.nonNegative(boost, "gravity")),
                new Charge(maxTicks, Json.nonNegative(charge, "moveScale"), launch),
                new Cut(Json.nonNegative(Json.object(object, "cut"), "factor")),
                new Camera(
                        Json.ticks(camera, "boostFovMinHoldTicks"),
                        Json.number(camera, "boostFov"),
                        Json.ticks(camera, "boostFovInTicks"),
                        Json.ticks(camera, "boostFovOutTicks"),
                        Json.nonNegative(camera, "landingMinFall"),
                        Json.nonNegative(camera, "landingDipPerBlock"),
                        Json.nonNegative(camera, "landingDipMax"),
                        Json.ticks(camera, "landingDipTicks"),
                        Json.ticks(camera, "landingRecoverTicks"),
                        Json.nonNegative(camera, "hardLandingFall"),
                        Json.nonNegative(camera, "hardLandingShake"),
                        Json.ticks(camera, "hardLandingShakeTicks")),
                new Sound(Json.string(sound, "boostSound"), Json.positive(sound, "boostPitch"),
                        Json.nonNegative(sound, "boostVolume"), Json.ticks(sound, "boostTick")));
    }

    /**
     * Preset A.
     *
     * @param freeTicks     air ticks that stay vanilla (2); sustain runs from the next one
     * @param sustainToTick the last air tick with reduced gravity (10)
     * @param gravity       gravity while sustained (0.028)
     */
    public record Boost(int freeTicks, int sustainToTick, double gravity) {
    }

    /**
     * Preset B.
     *
     * @param maxTicks     the longest squat (8)
     * @param moveScale    movement input while squatting (0.6)
     * @param launchSpeeds launch speed by squat ticks held, index 0 to {@code maxTicks}
     */
    public record Charge(int maxTicks, double moveScale, List<Double> launchSpeeds) {

        public Charge {
            launchSpeeds = List.copyOf(launchSpeeds);
        }

        public double launchSpeed(int squatTicks) {
            return launchSpeeds.get(Math.clamp(squatTicks, 0, maxTicks));
        }
    }

    /** Preset C: releasing while rising multiplies upward speed by {@code factor} (0.45) once. */
    public record Cut(double factor) {
    }

    /**
     * 02's jump camera.
     *
     * @param landingMinFall the shortest fall that dips the eye; above a tapped jump's 1.25 so a tap stays vanilla
     */
    public record Camera(
            int boostFovMinHoldTicks,
            double boostFov,
            int boostFovInTicks,
            int boostFovOutTicks,
            double landingMinFall,
            double landingDipPerBlock,
            double landingDipMax,
            int landingDipTicks,
            int landingRecoverTicks,
            double hardLandingFall,
            double hardLandingShake,
            int hardLandingShakeTicks
    ) {
    }

    public record Sound(String boostSound, double boostPitch, double boostVolume, int boostTick) {
    }
}
