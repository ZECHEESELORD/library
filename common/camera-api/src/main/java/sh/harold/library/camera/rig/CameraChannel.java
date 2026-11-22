package sh.harold.library.camera.rig;

import java.util.Locale;

/**
 * An additive channel of stage 5. Every cue curve is an offset from the channel's identity, so an empty cue set
 * leaves the camera exactly as the anchor placed it.
 *
 * <ul>
 *     <li>{@link #PITCH}: degrees added to the view pitch, positive looks down (Minecraft's {@code xRot} sign).
 *     Unwound: 360 is one full forward turn.</li>
 *     <li>{@link #ROLL}: degrees of roll about the view axis, positive raises the view's left side. Dutch is a roll
 *     layer.</li>
 *     <li>{@link #FOV}: degrees added to the vertical field of view.</li>
 *     <li>{@link #EYE_HEIGHT}: blocks added to the eye height, render only. A curve marked absolute
 *     ({@link sh.harold.library.camera.rig.cue.ChannelCurve#absoluteEyeHeight()}) is the drawn eye height itself.</li>
 *     <li>{@link #SHAKE}: rotational shake amplitude in degrees; the rig turns it into seeded noise on pitch and
 *     roll.</li>
 *     <li>{@link #LOOK_SCALE}: a look-input offset. Each cue's factor is {@code 1 + offset} and factors multiply, so
 *     an offset of {@code -0.8} turns mouse look down to 20%.</li>
 * </ul>
 *
 * <p>There is no yaw channel in wave 1: moves never take yaw (02, "the player always aims").
 */
public enum CameraChannel {
    PITCH,
    ROLL,
    FOV,
    EYE_HEIGHT,
    SHAKE,
    LOOK_SCALE;

    private static final CameraChannel[] VALUES = values();

    /** The lower-case name used in data files and telemetry, for example {@code look_scale}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CameraChannel fromId(String id) {
        for (CameraChannel channel : VALUES) {
            if (channel.id().equals(id)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("unknown camera channel '" + id + "'");
    }
}
