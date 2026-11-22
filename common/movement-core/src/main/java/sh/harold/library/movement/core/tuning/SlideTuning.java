package sh.harold.library.movement.core.tuning;

import sh.harold.library.movement.SlideVariant;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

/**
 * {@code slide.json}: 02 section 4.
 *
 * @param minSpeed         ground speed needed to start (0.25)
 * @param sprintSpeed      speed is raised to at least this before the boost (0.2806)
 * @param boost            start multiplier (1.25)
 * @param decay            per-tick multiplier on flat ground (0.96)
 * @param downhillGain     speed gained per tick downhill (0.01)
 * @param downhillMax      up to (0.5)
 * @param steerDegrees     steering per tick (1.5)
 * @param steerFromTick    (T4)
 * @param endSpeed         the slide ends below this speed (0.16)
 * @param maxFlatTicks     or after this many ticks on the flat (20)
 * @param releaseAfterTick or when sneak is released after this tick (T6)
 * @param cooldownTicks    after a slide ends (8)
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
        double decay,
        double downhillGain,
        double downhillMax,
        double steerDegrees,
        int steerFromTick,
        double endSpeed,
        int maxFlatTicks,
        int releaseAfterTick,
        int cooldownTicks,
        int jumpFromTick,
        double jumpBonus,
        double height,
        double eyeHeight,
        Camera camera,
        Sound sound
) {

    public static final String FILE = "slide.json";

    public SlideTuning withVariant(SlideVariant next) {
        return new SlideTuning(next, minSpeed, sprintSpeed, boost, decay, downhillGain, downhillMax, steerDegrees,
                steerFromTick, endSpeed, maxFlatTicks, releaseAfterTick, cooldownTicks, jumpFromTick, jumpBonus, height,
                eyeHeight, camera, sound);
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
                Json.positive(object, "decay"),
                Json.nonNegative(object, "downhillGain"),
                Json.nonNegative(object, "downhillMax"),
                Json.nonNegative(object, "steerDegrees"),
                Json.ticks(object, "steerFromTick"),
                Json.nonNegative(object, "endSpeed"),
                Json.ticks(object, "maxFlatTicks"),
                Json.ticks(object, "releaseAfterTick"),
                Json.ticks(object, "cooldownTicks"),
                Json.ticks(object, "jumpFromTick"),
                Json.nonNegative(object, "jumpBonus"),
                Json.positive(object, "height"),
                Json.positive(object, "eyeHeight"),
                new Camera(Json.positive(camera, "eyeLow"), Json.ticks(camera, "eyeLowTick"),
                        Json.number(camera, "fov"), Json.ticks(camera, "fovTick"),
                        Json.positive(camera, "fovReferenceSpeed"), Json.number(camera, "strafeRoll"),
                        Json.nonNegative(camera, "shake"), Json.nonNegative(camera, "shakeAboveSpeed"),
                        Json.ticks(camera, "eyeReturnTicks")),
                new Sound(Json.positive(sound, "pitch"), Math.max(1, Json.ticks(sound, "everyTicks"))));
    }

    /**
     * Slide camera, in 02's tick numbers.
     *
     * @param eyeLow            designed eye height, reached at the end of {@code eyeLowTick} (0.5 at T3)
     * @param fov               FOV kick, reached at the end of {@code fovTick} (8 at T4)
     * @param fovReferenceSpeed from {@code fovTick} the FOV follows speed: {@code fov * speed / this} (0.35)
     * @param strafeRoll        roll toward strafe input (2.5)
     * @param shake             shake while faster than {@code shakeAboveSpeed} (0.15 above 0.3)
     * @param eyeReturnTicks    the eye returns over this many ticks at the end (3)
     */
    public record Camera(double eyeLow, int eyeLowTick, double fov, int fovTick, double fovReferenceSpeed,
                         double strafeRoll, double shake, double shakeAboveSpeed, int eyeReturnTicks) {
    }

    public record Sound(double pitch, int everyTicks) {
    }
}
