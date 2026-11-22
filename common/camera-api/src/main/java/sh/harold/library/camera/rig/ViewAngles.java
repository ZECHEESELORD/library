package sh.harold.library.camera.rig;

import sh.harold.library.spatial.Quaternion;
import sh.harold.library.spatial.Vec3;

/**
 * View angles in degrees, in Minecraft's view convention: yaw 0 looks toward +Z and 90 toward -X; positive pitch looks
 * down; positive roll raises the view's left side (the telemetry {@code roll} column's sign). The camera looks along
 * its local -Z with +Y up.
 *
 * <p>The orientation is {@code Ry(180 - yaw) * Rx(-pitch) * Rz(-roll)}, the same composition the vanilla camera
 * builds with {@code rotationYXZ}, so roll and extra pitch are local rotations after the player's look. Angles are
 * unwound: pitch 360 is a full forward turn and gives the same orientation as pitch 0.
 */
public record ViewAngles(double yaw, double pitch, double roll) {

    public static final ViewAngles ZERO = new ViewAngles(0.0, 0.0, 0.0);

    private static final Vec3 FORWARD = new Vec3(0.0, 0.0, -1.0);
    private static final Vec3 UP = Vec3.UNIT_Y;
    private static final double GIMBAL = 0.999999;

    public ViewAngles {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(roll)) {
            throw new IllegalArgumentException("angles must be finite");
        }
    }

    public Quaternion toQuaternion() {
        return yawPitch(yaw, pitch).multiply(rollQuaternion(roll));
    }

    /** {@code Ry(180 - yaw) * Rx(-pitch)}. */
    public static Quaternion yawPitch(double yawDegrees, double pitchDegrees) {
        return Quaternion.axisAngle(Vec3.UNIT_Y, Math.PI - Math.toRadians(yawDegrees))
                .multiply(pitchQuaternion(pitchDegrees));
    }

    /** A local pitch rotation: positive looks down. */
    public static Quaternion pitchQuaternion(double pitchDegrees) {
        return Quaternion.axisAngle(Vec3.UNIT_X, -Math.toRadians(pitchDegrees));
    }

    /** A local roll rotation about the view axis: positive raises the view's left side. */
    public static Quaternion rollQuaternion(double rollDegrees) {
        return Quaternion.axisAngle(Vec3.UNIT_Z, -Math.toRadians(rollDegrees));
    }

    /**
     * Wrapped angles of an orientation: yaw in {@code (-180, 180]}, pitch in {@code [-90, 90]}, roll in
     * {@code (-180, 180]}. Looking straight up or down, roll is folded into yaw and reported as 0.
     */
    public static ViewAngles fromQuaternion(Quaternion orientation) {
        Quaternion q = orientation.normalize();
        Vec3 forward = q.rotate(FORWARD);
        double fy = Math.max(-1.0, Math.min(1.0, forward.y()));
        double pitch = Math.toDegrees(Math.asin(-fy));
        if (Math.abs(fy) > GIMBAL) {
            Vec3 up = q.rotate(UP);
            double yaw = fy < 0.0
                    ? Math.toDegrees(Math.atan2(-up.x(), up.z()))
                    : Math.toDegrees(Math.atan2(up.x(), -up.z()));
            return new ViewAngles(wrap(yaw), pitch, 0.0);
        }
        double yaw = Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        Quaternion residual = yawPitch(yaw, pitch).conjugate().multiply(q);
        double roll = -2.0 * Math.toDegrees(Math.atan2(residual.z(), residual.w()));
        return new ViewAngles(wrap(yaw), pitch, wrap(roll));
    }

    /** {@code degrees} wrapped into {@code (-180, 180]}. */
    public static double wrap(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped <= -180.0) {
            wrapped += 360.0;
        } else if (wrapped > 180.0) {
            wrapped -= 360.0;
        }
        return wrapped;
    }
}
