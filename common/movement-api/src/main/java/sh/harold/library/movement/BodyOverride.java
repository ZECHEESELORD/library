package sh.harold.library.movement;

/**
 * A hitbox and pose a move puts the body in: 0.9 high for the roll, 0.6 for the slide. The host applies it to the
 * entity's dimensions on both sides (gameplay) and maps {@code pose} to its render pose (what other players see).
 *
 * @param height    hitbox height in blocks; the width stays the player's
 * @param eyeHeight the body's eye height (raycasts, suffocation); the camera's drawn eye is a camera cue
 * @param pose      the render pose
 */
public record BodyOverride(double height, double eyeHeight, RenderPose pose) {

    public BodyOverride {
        if (!(height > 0.0) || !(eyeHeight > 0.0) || eyeHeight > height) {
            throw new IllegalArgumentException("need 0 < eyeHeight <= height, got " + eyeHeight + " / " + height);
        }
        if (pose == null) {
            throw new IllegalArgumentException("pose");
        }
    }

    public enum RenderPose {
        STANDING,
        /** Tucked: drawn crouching. */
        CROUCHING,
        /** Low: drawn in vanilla's crawl pose. */
        CRAWLING
    }
}
