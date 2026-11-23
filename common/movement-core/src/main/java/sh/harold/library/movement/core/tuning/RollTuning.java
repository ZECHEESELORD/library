package sh.harold.library.movement.core.tuning;

import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

import java.util.List;

/**
 * {@code roll.json}: 02 section 3.
 *
 * @param minFall         the shortest fall that can roll (3)
 * @param windowBefore    sneak may be pressed this many ticks before touchdown (6)
 * @param windowAfter     or this many after (2)
 * @param damageReduction fall damage is worked out as if the fall were this much shorter (10)
 * @param minSpeed        horizontal speed is raised to at least this, sprint speed (0.2806)
 * @param boost           then multiplied by this for the roll (1.15)
 * @param ticks           the roll's length, T0 to T9 (10)
 * @param decayTicks      the boost decays over this many ticks after (6)
 * @param height          hitbox height while rolling (0.9)
 * @param eyeHeight       body eye height while rolling (half the hitbox, scaled like vanilla's 0.9 of height)
 * @param jumpBufferFrom  a jump pressed from this tick fires at T10 (7)
 * @param lookScale       look input scale during the roll (0.2)
 */
public record RollTuning(
        double minFall,
        int windowBefore,
        int windowAfter,
        double damageReduction,
        double minSpeed,
        double boost,
        int ticks,
        int decayTicks,
        double height,
        double eyeHeight,
        int jumpBufferFrom,
        double lookScale,
        Camera camera,
        Sound sound
) {

    public static final String FILE = "roll.json";

    public static RollTuning decode(JsonValue root) {
        JsonObject object = root.asObject();
        JsonObject camera = Json.object(object, "camera");
        JsonObject sound = Json.object(object, "sound");
        return new RollTuning(
                Json.nonNegative(object, "minFall"),
                Json.ticks(object, "windowBefore"),
                Json.ticks(object, "windowAfter"),
                Json.nonNegative(object, "damageReduction"),
                Json.nonNegative(object, "minSpeed"),
                Json.positive(object, "boost"),
                Json.ticks(object, "ticks"),
                Json.ticks(object, "decayTicks"),
                Json.positive(object, "height"),
                Json.positive(object, "eyeHeight"),
                Json.ticks(object, "jumpBufferFrom"),
                Json.nonNegative(object, "lookScale"),
                new Camera(Json.number(camera, "turnDegrees"), Json.ticks(camera, "halfTurnTick"),
                        Json.number(camera, "fov"), Json.ticks(camera, "fovPeakTick"), Json.ticks(camera, "fovEndTick"),
                        Json.positive(camera, "eyeLow"), Json.ticks(camera, "eyeLowTick"),
                        Json.ticks(camera, "eyeRiseFromTick"), Json.ticks(camera, "eyeRiseTick"),
                        Json.number(camera, "reducedDip"), Json.nonNegative(camera, "sideMinDegrees"),
                        Json.nonNegative(camera, "sideMaxDegrees"), Json.number(camera, "sideRoll")),
                new Sound(Json.string(sound, "start"), Json.positive(sound, "startPitch"),
                        Json.integers(sound, "stepTicks")));
    }

    /**
     * Roll camera, in 02's tick numbers ({@code T}).
     *
     * @param turnDegrees     the forward pitch turn (360)
     * @param halfTurnTick    half the turn has passed by the end of this tick (T5)
     * @param fov             FOV kick (6)
     * @param fovPeakTick     (T3)
     * @param fovEndTick      FOV back to normal by the start of this tick (T10)
     * @param eyeLow          designed eye height at the bottom of the tuck (0.9)
     * @param eyeLowTick      reached at the end of this tick (T2)
     * @param eyeRiseFromTick held until the end of this tick (T6)
     * @param eyeRiseTick     back to standing at the end of this tick (T9)
     * @param reducedDip      the pitch dip in place of a turn: Reduced always, Full for a sideways roll (25)
     * @param sideMinDegrees  travel more than this from the look is sideways: no flip (60, RO1)
     * @param sideMaxDegrees  and more than this is backward: a backward turn (120)
     * @param sideRoll        a sideways roll leans this far toward the travel side (8); Reduced drops it
     */
    public record Camera(double turnDegrees, int halfTurnTick, double fov, int fovPeakTick, int fovEndTick,
                         double eyeLow, int eyeLowTick, int eyeRiseFromTick, int eyeRiseTick, double reducedDip,
                         double sideMinDegrees, double sideMaxDegrees, double sideRoll) {
    }

    public record Sound(String start, double startPitch, List<Integer> stepTicks) {

        public Sound {
            stepTicks = List.copyOf(stepTicks);
        }
    }
}
