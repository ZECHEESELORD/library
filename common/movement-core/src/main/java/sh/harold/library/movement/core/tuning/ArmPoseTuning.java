package sh.harold.library.movement.core.tuning;

/**
 * A first-person arm pose in a tuning file, written as {@code [x, y, z, rotX, rotY, rotZ]}: blocks outward, up and
 * toward the viewer, then degrees (see {@code sh.harold.library.camera.rig.ArmPose}).
 */
public record ArmPoseTuning(double x, double y, double z, double rotX, double rotY, double rotZ) {

    public static final ArmPoseTuning ZERO = new ArmPoseTuning(0, 0, 0, 0, 0, 0);

    /** The value on axis {@code index}, 0-5 in the order above. */
    public double get(int index) {
        return switch (index) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            case 3 -> rotX;
            case 4 -> rotY;
            case 5 -> rotZ;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }
}
