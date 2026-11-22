package sh.harold.library.camera.rig.stage;

import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.ViewAngles;

/**
 * Stage 1: resolves the frame the camera starts from. Wave 1 has the player's eye; an entity, a point, a display, a
 * {@code Frame3} the game supplies, a rig bone and a projectile follow in waves 2 and 3.
 */
@FunctionalInterface
public interface AnchorStage {

    /** The state at the anchor, every later field at identity. */
    CameraState anchor(RigFrame frame);

    /** The player's own eye and look, exactly as the host computed them. */
    AnchorStage EYE = frame -> CameraState.anchored(frame.input().eyePosition(),
            new ViewAngles(frame.input().yaw(), frame.input().pitch(), 0.0),
            frame.input().fovDegrees());
}
