package sh.harold.library.camera.rig;

/**
 * An offset of one first-person arm (and the item it holds), render only. The arm channels
 * ({@link CameraChannel#ARM_MAIN_X} and the rest) sum into one pose per arm each frame; a host adapter applies it about
 * the arm's rest position, so a zero pose leaves vanilla's arm exactly as it is.
 *
 * <p>Axes are the hand's view space, mirrored per side so one pose reads the same on either arm:
 *
 * @param x    blocks outward, away from the screen centre on that arm's side
 * @param y    blocks up
 * @param z    blocks toward the viewer (negative reaches forward)
 * @param rotX degrees about the view's x axis (negative tips the arm's top away from the viewer)
 * @param rotY degrees about the view's y axis, mirrored per side (positive turns the arm outward)
 * @param rotZ degrees about the view axis, mirrored per side (positive tilts the arm's top outward)
 */
public record ArmPose(double x, double y, double z, double rotX, double rotY, double rotZ) {

    public static final ArmPose IDENTITY = new ArmPose(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    public ArmPose {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(rotX)
                || !Double.isFinite(rotY) || !Double.isFinite(rotZ)) {
            throw new IllegalArgumentException("arm pose values must be finite");
        }
    }

    /** Whether every offset is zero, so the arm is drawn exactly as vanilla draws it. */
    public boolean isIdentity() {
        return x == 0.0 && y == 0.0 && z == 0.0 && rotX == 0.0 && rotY == 0.0 && rotZ == 0.0;
    }

    /** The value on axis {@code index}: 0-2 translation x, y, z; 3-5 rotation x, y, z. */
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

    /** A pose from six values in {@link #get(int)} order. */
    public static ArmPose of(double[] values, int offset) {
        return new ArmPose(values[offset], values[offset + 1], values[offset + 2], values[offset + 3],
                values[offset + 4], values[offset + 5]);
    }
}
