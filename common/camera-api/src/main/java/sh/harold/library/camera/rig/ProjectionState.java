package sh.harold.library.camera.rig;

/**
 * Stage 6 output: changes to the projection and the final image. Wave 1 defines the fields and leaves them at
 * identity; camera squash arrives in wave 2, digital punch-in and lens shift in wave 3.
 *
 * @param squash      projection squash with area kept: {@code sy = squash}, {@code sx = 1 / squash}
 * @param shiftX      lens shift, in fractions of the image width
 * @param shiftY      lens shift, in fractions of the image height
 * @param imageScale  digital punch-in (or zoom out below 1) of the rendered image about its offset point
 * @param imageOffsetX digital pan, in fractions of the image width
 * @param imageOffsetY digital pan, in fractions of the image height
 * @param letterbox   fraction of the image height covered by bars, top and bottom together
 */
public record ProjectionState(
        double squash,
        double shiftX,
        double shiftY,
        double imageScale,
        double imageOffsetX,
        double imageOffsetY,
        double letterbox
) {

    public static final ProjectionState IDENTITY = new ProjectionState(1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0);

    public ProjectionState {
        if (!(squash > 0.0) || !Double.isFinite(squash)) {
            throw new IllegalArgumentException("squash must be finite and positive");
        }
        if (!(imageScale > 0.0) || !Double.isFinite(imageScale)) {
            throw new IllegalArgumentException("imageScale must be finite and positive");
        }
        if (!Double.isFinite(shiftX) || !Double.isFinite(shiftY)
                || !Double.isFinite(imageOffsetX) || !Double.isFinite(imageOffsetY)) {
            throw new IllegalArgumentException("shifts and offsets must be finite");
        }
        if (!(letterbox >= 0.0 && letterbox < 1.0)) {
            throw new IllegalArgumentException("letterbox must be in [0, 1)");
        }
    }

    public boolean isIdentity() {
        return equals(IDENTITY);
    }
}
