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
 *     <li>{@code ARM_MAIN_*} and {@code ARM_OFF_*}: the first-person arm pose of the main and off hand, render only
 *     (see {@link ArmPose} for axes and signs). {@code _X}, {@code _Y}, {@code _Z} are blocks of translation and
 *     {@code _ROT_X}, {@code _ROT_Y}, {@code _ROT_Z} degrees of rotation about the arm's rest position. With no arm
 *     curve live the arms are vanilla's, untouched.</li>
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
    LOOK_SCALE,
    ARM_MAIN_X,
    ARM_MAIN_Y,
    ARM_MAIN_Z,
    ARM_MAIN_ROT_X,
    ARM_MAIN_ROT_Y,
    ARM_MAIN_ROT_Z,
    ARM_OFF_X,
    ARM_OFF_Y,
    ARM_OFF_Z,
    ARM_OFF_ROT_X,
    ARM_OFF_ROT_Y,
    ARM_OFF_ROT_Z;

    /** The six main-hand arm channels in {@link ArmPose#get(int)} order. */
    public static final java.util.List<CameraChannel> ARM_MAIN = java.util.List.of(ARM_MAIN_X, ARM_MAIN_Y, ARM_MAIN_Z,
            ARM_MAIN_ROT_X, ARM_MAIN_ROT_Y, ARM_MAIN_ROT_Z);

    /** The six off-hand arm channels in {@link ArmPose#get(int)} order. */
    public static final java.util.List<CameraChannel> ARM_OFF = java.util.List.of(ARM_OFF_X, ARM_OFF_Y, ARM_OFF_Z,
            ARM_OFF_ROT_X, ARM_OFF_ROT_Y, ARM_OFF_ROT_Z);

    private static final CameraChannel[] VALUES = values();

    /** The lower-case name used in data files and telemetry, for example {@code look_scale}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether this is one of the twelve arm-pose channels. */
    public boolean isArm() {
        return ordinal() >= ARM_MAIN_X.ordinal();
    }

    /** For an arm channel, its index in the twelve arm values: main 0-5, off 6-11, each in {@link ArmPose} order. */
    public int armIndex() {
        if (!isArm()) {
            throw new IllegalStateException(this + " is not an arm channel");
        }
        return ordinal() - ARM_MAIN_X.ordinal();
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
