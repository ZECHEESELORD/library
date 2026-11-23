package sh.harold.library.movement.core.tuning;

import sh.harold.library.movement.SlideVariant;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

/**
 * {@code slide.json}: 02 section 4 with the 2026-11-23 rulings S1-S4.
 *
 * @param minSpeed         ground speed needed to start (0.25)
 * @param sprintSpeed      speed is raised to at least this before the boost (0.2806)
 * @param boost            start multiplier with the boost full (1.25)
 * @param refillTicks      the boost refills linearly over this many ticks after a slide ends (20, S3); a slide is
 *                         never refused
 * @param decay            per-tick multiplier, always (0.96)
 * @param slopeGain        each tick, speed squared gains this times the blocks descended that tick (0.048, S2)
 * @param maxSpeed         speed cap (0.5)
 * @param steerDegrees     steering per tick (1.5)
 * @param steerFromTick    (T4)
 * @param endSpeed         the slide ends below this speed (0.19, S1)
 * @param maxAirTicks      or after more than this many airborne ticks in a row (4)
 * @param releaseAfterTick or when sneak is released after this tick (T6)
 * @param jumpFromTick     slide-jump allowed from (T4)
 * @param jumpBonus        slide-jump speed bonus (0.1)
 * @param height           hitbox height (0.6)
 * @param eyeHeight        body eye height
 */
public record SlideTuning(
        SlideVariant variant,
        double minSpeed,
        double sprintSpeed,
        double boost,
        int refillTicks,
        double decay,
        double slopeGain,
        double maxSpeed,
        double steerDegrees,
        int steerFromTick,
        double endSpeed,
        int maxAirTicks,
        int releaseAfterTick,
        int jumpFromTick,
        double jumpBonus,
        double height,
        double eyeHeight,
        Camera camera,
        Sound sound
) {

    public static final String FILE = "slide.json";

    public SlideTuning withVariant(SlideVariant next) {
        return new SlideTuning(next, minSpeed, sprintSpeed, boost, refillTicks, decay, slopeGain, maxSpeed,
                steerDegrees, steerFromTick, endSpeed, maxAirTicks, releaseAfterTick, jumpFromTick, jumpBonus, height,
                eyeHeight, camera, sound);
    }

    /** The boost refill {@code r} in [0, 1], {@code ticksSinceEnd} after the last slide ended (S3). */
    public double refill(long ticksSinceEnd) {
        if (refillTicks <= 0) {
            return 1.0;
        }
        return Math.clamp((double) ticksSinceEnd / refillTicks, 0.0, 1.0);
    }

    /** The start multiplier at refill {@code r}: {@code 1 + (boost - 1) r}. */
    public double boostAt(double refill) {
        return 1.0 + (boost - 1.0) * refill;
    }

    public static SlideTuning decode(JsonValue root) {
        JsonObject object = root.asObject();
        JsonObject camera = Json.object(object, "camera");
        JsonObject sound = Json.object(object, "sound");
        SlideVariant variant;
        try {
            variant = SlideVariant.parse(Json.string(object, "variant"));
        } catch (IllegalArgumentException exception) {
            throw new DataDecodeException(object.get("variant"), "variant must be S1 or S2");
        }
        return new SlideTuning(
                variant,
                Json.nonNegative(object, "minSpeed"),
                Json.nonNegative(object, "sprintSpeed"),
                Json.positive(object, "boost"),
                Json.ticks(object, "refillTicks"),
                Json.positive(object, "decay"),
                Json.nonNegative(object, "slopeGain"),
                Json.positive(object, "maxSpeed"),
                Json.nonNegative(object, "steerDegrees"),
                Json.ticks(object, "steerFromTick"),
                Json.nonNegative(object, "endSpeed"),
                Json.ticks(object, "maxAirTicks"),
                Json.ticks(object, "releaseAfterTick"),
                Json.ticks(object, "jumpFromTick"),
                Json.nonNegative(object, "jumpBonus"),
                Json.positive(object, "height"),
                Json.positive(object, "eyeHeight"),
                new Camera(Json.positive(camera, "eyeLow"), Json.ticks(camera, "eyeLowTick"),
                        Json.number(camera, "fov"), Json.number(camera, "fovMin"), Json.ticks(camera, "fovTick"),
                        Json.positive(camera, "fovReferenceSpeed"), Json.number(camera, "strafeRoll"),
                        Json.nonNegative(camera, "shake"), Json.nonNegative(camera, "shakeAboveSpeed"),
                        Json.ticks(camera, "eyeReturnTicks")),
                new Sound(Json.positive(sound, "pitch"), Math.max(1, Json.ticks(sound, "everyTicks"))));
    }

    /**
     * Slide camera, in 02's tick numbers.
     *
     * @param eyeLow            designed eye height, reached at the end of {@code eyeLowTick} (0.5 at T3)
     * @param fov               FOV kick with the boost full, reached at the end of {@code fovTick} (8 at T4)
     * @param fovMin            the kick with the boost empty: the kick is {@code fovMin + (fov - fovMin) r} (4, S3)
     * @param fovReferenceSpeed from {@code fovTick} the FOV follows speed: {@code kick * speed / this} (0.35)
     * @param strafeRoll        roll toward strafe input (2.5)
     * @param shake             shake while faster than {@code shakeAboveSpeed} (0.15 above 0.3)
     * @param eyeReturnTicks    the eye returns over this many ticks at the end (3)
     */
    public record Camera(double eyeLow, int eyeLowTick, double fov, double fovMin, int fovTick,
                         double fovReferenceSpeed, double strafeRoll, double shake, double shakeAboveSpeed,
                         int eyeReturnTicks) {

        /** The FOV kick at refill {@code r}. */
        public double kick(double refill) {
            return fovMin + (fov - fovMin) * refill;
        }
    }

    public record Sound(double pitch, int everyTicks) {
    }
}
