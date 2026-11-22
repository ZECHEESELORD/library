package sh.harold.library.movement.core.tuning;

import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

/**
 * {@code wall_jump.json}: 02 section 5, the kick and the wall slide.
 *
 * @param minAirTicks       airborne this long first (3)
 * @param wallMinHeight     the wall must be at least this tall (1.5)
 * @param reach             within this of the hitbox's side (0.1)
 * @param pressBufferTicks  a jump press is buffered this long (3)
 * @param afterContactTicks and counts this long after contact is lost (3)
 * @param kickUp            upward speed of the kick (0.48)
 * @param kickAway          horizontal speed away from the wall (0.32)
 * @param kickInput         plus this along the movement keys (0.12)
 * @param hitPitch          the wall block's hit sound pitch on T0 (1.2)
 */
public record WallJumpTuning(
        int minAirTicks,
        double wallMinHeight,
        double reach,
        int pressBufferTicks,
        int afterContactTicks,
        double kickUp,
        double kickAway,
        double kickInput,
        Slide slide,
        Camera camera,
        double hitPitch
) {

    public static final String FILE = "wall_jump.json";

    public static WallJumpTuning decode(JsonValue root) {
        JsonObject object = root.asObject();
        JsonObject slide = Json.object(object, "slide");
        JsonObject camera = Json.object(object, "camera");
        return new WallJumpTuning(
                Json.ticks(object, "minAirTicks"),
                Json.positive(object, "wallMinHeight"),
                Json.nonNegative(object, "reach"),
                Json.ticks(object, "pressBufferTicks"),
                Json.ticks(object, "afterContactTicks"),
                Json.number(object, "kickUp"),
                Json.number(object, "kickAway"),
                Json.number(object, "kickInput"),
                new Slide(Json.positive(slide, "maxFall"), Json.ticks(slide, "maxTicks"),
                        Json.nonNegative(slide, "pushThreshold"), Math.max(1, Json.ticks(slide, "effectEveryTicks")),
                        Json.string(slide, "sound"), Json.positive(slide, "pitch")),
                new Camera(Json.number(camera, "roll"), Json.ticks(camera, "inTick"), Json.ticks(camera, "holdTick"),
                        Json.ticks(camera, "backTick"), Json.number(camera, "fov")),
                Json.positive(object, "hitPitch"));
    }

    /**
     * The wall slide.
     *
     * @param maxFall          fall speed cap (0.12)
     * @param maxTicks         for up to (24)
     * @param pushThreshold    how squarely the movement keys must push into the wall (dot product)
     * @param effectEveryTicks dust and sound every (4)
     */
    public record Slide(double maxFall, int maxTicks, double pushThreshold, int effectEveryTicks, String sound,
                        double pitch) {
    }

    /** Kick camera in 02's ticks: roll and FOV reach full by the end of {@code inTick}, hold to {@code holdTick}. */
    public record Camera(double roll, int inTick, int holdTick, int backTick, double fov) {
    }
}
