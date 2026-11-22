package sh.harold.library.movement.core.tuning;

import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

/**
 * {@code movement.json}: the rules every move shares (02, "Rules shared by every move") and the server's claim
 * handling.
 *
 * @param inputBufferTicks         how long a jump or sneak press is remembered (4)
 * @param coyoteTicks              how long after walking off an edge a jump still fires (3)
 * @param chordTicks               how close together sneak and jump count as one chord (2, slide S2)
 * @param positionTolerance        how far a claim's position may sit from the server's before it is a desync
 * @param pendingClaimTimeoutTicks how long a deferred claim waits for the server's view to catch up
 * @param latencyGraceTicks        how long the server holds a landing's fall damage for a late roll claim, on top of
 *                                 the roll's own after-window
 */
public record CoreTuning(
        int inputBufferTicks,
        int coyoteTicks,
        int chordTicks,
        double positionTolerance,
        int pendingClaimTimeoutTicks,
        int latencyGraceTicks
) {

    public static final String FILE = "movement.json";

    public static CoreTuning decode(JsonValue root) {
        JsonObject object = root.asObject();
        JsonObject server = Json.object(object, "server");
        return new CoreTuning(
                Json.ticks(object, "inputBufferTicks"),
                Json.ticks(object, "coyoteTicks"),
                Json.ticks(object, "chordTicks"),
                Json.positive(server, "positionTolerance"),
                Json.ticks(server, "pendingClaimTimeoutTicks"),
                Json.ticks(server, "latencyGraceTicks"));
    }
}
