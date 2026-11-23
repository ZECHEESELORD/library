package sh.harold.library.movement.core.tuning;

import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

import java.util.List;

/**
 * {@code mantle.json}: 02 section 2 with the 2026-11-23 rulings MA1-MA18. Camera and arm curves are keys in move ticks
 * ({@code [3, 15]} is 15 at the end of T3), eased from rest at the start of T0.
 *
 * @param reach           how far past the front of the hitbox a ledge may be (0.45)
 * @param minHeight       the lowest ledge top a climb catches, above the feet (0.9)
 * @param maxHeight       the highest ledge top (1.6)
 * @param vaultMinHeight  the lowest ledge top a sprinting body vaults (0.8)
 * @param vaultMaxHeight  a sprinting body vaults ledges up to this height and climbs higher ones (1.3)
 * @param groundMaxHeight from the ground, a ledge top at or below this mantles only when sprinting (1.25, MA5)
 * @param sprintSpeed     "sprinting" is the sprint state with horizontal speed at least this (0.2)
 * @param approachDegrees movement input must point within this of the wall's inward normal (45, MA4)
 * @param maxFall         no catch once the fall is longer than this (6, MA16)
 * @param headroom        clear space above the ledge (1.8)
 * @param depth           clear depth on top of the ledge (0.6)
 * @param hitPitch        pitch of the ledge block's hit sound on T0 (0.8)
 */
public record MantleTuning(
        double reach,
        double minHeight,
        double maxHeight,
        double vaultMinHeight,
        double vaultMaxHeight,
        double groundMaxHeight,
        double sprintSpeed,
        double approachDegrees,
        double maxFall,
        double headroom,
        double depth,
        Vault vault,
        Climb climb,
        Refusal refusal,
        double hitPitch
) {

    public static final String FILE = "mantle.json";

    public static MantleTuning decode(JsonValue root) {
        JsonObject object = root.asObject();
        JsonObject vault = Json.object(object, "vault");
        JsonObject vaultCamera = Json.object(vault, "camera");
        JsonObject climb = Json.object(object, "climb");
        JsonObject camera = Json.object(climb, "camera");
        JsonObject refusal = Json.object(object, "refusal");
        MantleTuning tuning = new MantleTuning(
                Json.positive(object, "reach"),
                Json.positive(object, "minHeight"),
                Json.positive(object, "maxHeight"),
                Json.positive(object, "vaultMinHeight"),
                Json.positive(object, "vaultMaxHeight"),
                Json.nonNegative(object, "groundMaxHeight"),
                Json.nonNegative(object, "sprintSpeed"),
                Json.positive(object, "approachDegrees"),
                Json.positive(object, "maxFall"),
                Json.positive(object, "headroom"),
                Json.positive(object, "depth"),
                new Vault(Json.ticks(vault, "ticks"), Json.nonNegative(vault, "sprintExitSpeed"),
                        Json.number(vault, "swingEyeAboveLedge"), Json.ticks(vault, "swingTick"),
                        Json.ticks(vault, "riseTick"), Json.nonNegative(vault, "forward"),
                        Json.ticks(vault, "hopFromTick"), Json.ticks(vault, "hopToTick"),
                        Json.nonNegative(vault, "slideMinSpeed"),
                        new VaultCamera(Json.keys(vaultCamera, "pitch"),
                                Json.nonNegative(vaultCamera, "reducedPitchScale"),
                                Json.armKeys(vaultCamera, "mainArm"))),
                new Climb(Json.ticks(climb, "ticks"), Json.ticks(climb, "settleTick"), Json.ticks(climb, "riseTick"),
                        Json.ticks(climb, "forwardFromTick"), Json.ticks(climb, "forwardToTick"),
                        Json.nonNegative(climb, "forward"), Json.ticks(climb, "inputTick"),
                        Json.ticks(climb, "cancelWindowTicks"), Json.nonNegative(climb, "walkExitSpeed"),
                        Json.nonNegative(climb, "sprintExitSpeed"), Json.ticks(climb, "bufferFromTick"),
                        Json.ticks(climb, "hopFromTick"), Json.ticks(climb, "hopToTick"),
                        Json.nonNegative(climb, "hopSpeed"), Json.ticks(climb, "strafeFromTick"),
                        Json.nonNegative(climb, "strafeSpeed"), Json.nonNegative(climb, "slideMinSpeed"),
                        new ClimbCamera(Json.keys(camera, "eye"), Json.keys(camera, "pitch"),
                                Json.nonNegative(camera, "reducedPitchScale"), Json.keys(camera, "roll"),
                                Json.armKeys(camera, "arms"), Json.number(camera, "hopFov"),
                                Json.ticks(camera, "hopFovInTicks"), Json.ticks(camera, "hopFovOutTicks"))),
                new Refusal(Math.max(1, Json.ticks(refusal, "ticks")), Json.positive(refusal, "hitPitch"),
                        Json.armKeys(refusal, "arms")),
                Json.positive(object, "hitPitch"));
        if (tuning.vaultMinHeight > tuning.vaultMaxHeight || tuning.minHeight > tuning.maxHeight
                || tuning.vaultMaxHeight > tuning.maxHeight) {
            throw new DataDecodeException(object,
                    "heights must run vaultMinHeight <= vaultMaxHeight <= maxHeight and minHeight <= maxHeight");
        }
        Climb c = tuning.climb;
        if (c.inputTick >= c.ticks || c.hopFromTick > c.hopToTick || c.bufferFromTick > c.hopFromTick) {
            throw new DataDecodeException(climb, "climb ticks must run bufferFromTick <= hopFromTick <= hopToTick"
                    + " and inputTick < ticks");
        }
        if (tuning.vault.hopFromTick > tuning.vault.hopToTick) {
            throw new DataDecodeException(vault, "hopFromTick must not be after hopToTick");
        }
        return tuning;
    }

    /** The lowest ledge top any mantle may take, for probes and the server check. */
    public double lowestHeight() {
        return Math.min(minHeight, vaultMinHeight);
    }

    /**
     * The vault (sprinting, ledge 0.8 to 1.3 above the feet, MA6).
     *
     * @param ticks              length; full control returns on this tick (5)
     * @param sprintExitSpeed    the exit keeps the entry speed, raised to this when sprint is held (0.2806, MA7)
     * @param swingEyeAboveLedge eye height above the ledge at the end of {@code swingTick} (1.2)
     * @param swingTick          (2)
     * @param riseTick           the tick the feet reach the ledge top (4)
     * @param forward            how far past the wall face the front of the body ends (0.5)
     * @param hopFromTick        a jump pressed from this tick... (3, MA10)
     * @param hopToTick          ...to this one fires on the exit tick with the speed kept (5)
     * @param slideMinSpeed      sneak held on the exit tick at this speed or more hands off to the slide (0.25, MA11)
     */
    public record Vault(int ticks, double sprintExitSpeed, double swingEyeAboveLedge, int swingTick, int riseTick,
                        double forward, int hopFromTick, int hopToTick, double slideMinSpeed, VaultCamera camera) {
    }

    /**
     * Vault camera: the nod (MA2) and the planting arm (MA3).
     *
     * @param pitch             pitch keys, degrees (6 at T2, 0 at T5)
     * @param reducedPitchScale factor on the nod in Reduced (0.5)
     * @param mainArm           main-hand arm keys
     */
    public record VaultCamera(List<TickKey> pitch, double reducedPitchScale, List<ArmKey> mainArm) {
    }

    /**
     * The climb (MA6: not sprinting, or a ledge above the vault window).
     *
     * @param ticks             length; the move ends on this tick (9)
     * @param settleTick        the hang ends and the pull starts after this tick (2)
     * @param riseTick          the feet reach the ledge top at the end of this tick (6)
     * @param forwardFromTick   forward motion starts after this tick, once the feet clear the top (5)
     * @param forwardToTick     and ends at the end of this one (7)
     * @param forward           how far past the wall face the front of the body ends (0.35, MA17)
     * @param inputTick         movement input and buffered presses fire on this tick (7)
     * @param cancelWindowTicks sneak or jump during T1 to this tick cancels (2)
     * @param walkExitSpeed     exit speed with forward held (vanilla walking)
     * @param sprintExitSpeed   exit speed with forward and sprint held, or sprint pressed from {@code bufferFromTick}
     *                          (0.2806, MA8)
     * @param bufferFromTick    jump, sneak and sprint pressed from this tick are buffered to {@code inputTick} (4, MA9)
     * @param hopFromTick       a jump pressed from this tick... (5, MA10)
     * @param hopToTick         ...to this one is a mantle hop: a jump plus {@code hopSpeed} forward
     * @param hopSpeed          (0.1)
     * @param strafeFromTick    strafe input moves the body sideways from this tick (5)
     * @param strafeSpeed       blocks per tick (0.05)
     * @param slideMinSpeed     a buffered sneak at this exit speed or more hands off to the slide (0.25)
     */
    public record Climb(int ticks, int settleTick, int riseTick, int forwardFromTick, int forwardToTick,
                        double forward, int inputTick, int cancelWindowTicks, double walkExitSpeed,
                        double sprintExitSpeed, int bufferFromTick, int hopFromTick, int hopToTick, double hopSpeed,
                        int strafeFromTick, double strafeSpeed, double slideMinSpeed, ClimbCamera camera) {
    }

    /**
     * Climb camera.
     *
     * @param eye               eye-height keys: the catch bounce (MA13)
     * @param pitch             pitch keys, looking down at the hands (MA1)
     * @param reducedPitchScale factor on the pitch in Reduced (0.5)
     * @param roll              roll keys toward the leading hand (MA14); Reduced drops roll
     * @param arms              arm keys for both hands (MA3)
     * @param hopFov            the mantle hop's FOV kick (2, MA10), the only FOV a mantle adds (MA18)
     * @param hopFovInTicks     (1)
     * @param hopFovOutTicks    (4)
     */
    public record ClimbCamera(List<TickKey> eye, List<TickKey> pitch, double reducedPitchScale, List<TickKey> roll,
                              List<ArmKey> arms, double hopFov, int hopFovInTicks, int hopFovOutTicks) {
    }

    /**
     * A refused mantle (MA12): a slap with no motion change.
     *
     * @param ticks    how long the slap shows (4)
     * @param hitPitch pitch of the wall block's hit sound (1.0)
     * @param arms     arm keys for both hands, touching the wall
     */
    public record Refusal(int ticks, double hitPitch, List<ArmKey> arms) {
    }
}
