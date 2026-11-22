package sh.harold.library.movement.core.tuning;

import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

/**
 * {@code mantle.json}: 02 section 2.
 *
 * @param reach          how far past the front of the hitbox a ledge may be (0.45)
 * @param minHeight      the lowest ledge top above the feet (0.9)
 * @param maxHeight      the highest (1.6)
 * @param vaultMaxHeight ledges up to this height vault; higher ones climb (1.1)
 * @param headroom       clear space above the ledge (1.8)
 * @param depth          clear depth on top of the ledge (0.6)
 * @param hitPitch       pitch of the ledge block's hit sound on T0 (0.8)
 */
public record MantleTuning(
        double reach,
        double minHeight,
        double maxHeight,
        double vaultMaxHeight,
        double headroom,
        double depth,
        Vault vault,
        Climb climb,
        double hitPitch
) {

    public static final String FILE = "mantle.json";

    public static MantleTuning decode(JsonValue root) {
        JsonObject object = root.asObject();
        JsonObject vault = Json.object(object, "vault");
        JsonObject climb = Json.object(object, "climb");
        JsonObject camera = Json.object(climb, "camera");
        MantleTuning tuning = new MantleTuning(
                Json.positive(object, "reach"),
                Json.positive(object, "minHeight"),
                Json.positive(object, "maxHeight"),
                Json.positive(object, "vaultMaxHeight"),
                Json.positive(object, "headroom"),
                Json.positive(object, "depth"),
                new Vault(Json.ticks(vault, "ticks"), Json.nonNegative(vault, "keepHorizontal"),
                        Json.number(vault, "swingEyeAboveLedge"), Json.ticks(vault, "swingTick"),
                        Json.ticks(vault, "riseTick"), Json.nonNegative(vault, "forward")),
                new Climb(Json.ticks(climb, "ticks"), Json.ticks(climb, "settleTick"), Json.ticks(climb, "riseTick"),
                        Json.ticks(climb, "forwardFromTick"), Json.ticks(climb, "forwardToTick"),
                        Json.nonNegative(climb, "forward"), Json.ticks(climb, "inputTick"),
                        Json.ticks(climb, "cancelWindowTicks"), Json.nonNegative(climb, "exitSpeed"),
                        new ClimbCamera(Json.nonNegative(camera, "bounce"), Json.number(camera, "hangPitch"),
                                Json.number(camera, "hangRoll"), Json.number(camera, "pullPitch"),
                                Json.ticks(camera, "pullPitchTick"), Json.ticks(camera, "pullPitchEndTick"))),
                Json.positive(object, "hitPitch"));
        if (tuning.minHeight > tuning.vaultMaxHeight || tuning.vaultMaxHeight > tuning.maxHeight) {
            throw new DataDecodeException(object, "heights must run minHeight <= vaultMaxHeight <= maxHeight");
        }
        return tuning;
    }

    /**
     * The vault (ledge 0.9 to 1.1 above the feet).
     *
     * @param ticks              length; full control returns on this tick (5)
     * @param keepHorizontal     horizontal speed kept through the vault (0.8)
     * @param swingEyeAboveLedge eye height above the ledge at the end of {@code swingTick} (1.2)
     * @param swingTick          (2)
     * @param riseTick           the tick the feet reach the ledge top (4)
     * @param forward            how far past the wall face the front of the body ends (0.5)
     */
    public record Vault(int ticks, double keepHorizontal, double swingEyeAboveLedge, int swingTick, int riseTick,
                        double forward) {
    }

    /**
     * The climb (ledge above 1.1).
     *
     * @param ticks             length; full control on this tick (9)
     * @param settleTick        the hang ends and the pull starts after this tick (2)
     * @param riseTick          the feet reach the ledge top at the end of this tick (6)
     * @param forwardFromTick   forward motion starts after this tick, once the feet clear the top (5)
     * @param forwardToTick     and ends at the end of this one (7)
     * @param forward           how far past the wall face the front of the body ends (0.35)
     * @param inputTick         movement input accepted from this tick (7)
     * @param cancelWindowTicks sneak or jump during T0 to this tick cancels (2)
     * @param exitSpeed         horizontal speed when input returns: vanilla walking speed
     */
    public record Climb(int ticks, int settleTick, int riseTick, int forwardFromTick, int forwardToTick,
                        double forward, int inputTick, int cancelWindowTicks, double exitSpeed, ClimbCamera camera) {
    }

    /** Climb camera: the catch bounce, the hang's pitch and roll, the pull's pitch dip (degrees as 02 writes them). */
    public record ClimbCamera(double bounce, double hangPitch, double hangRoll, double pullPitch, int pullPitchTick,
                              int pullPitchEndTick) {
    }
}
