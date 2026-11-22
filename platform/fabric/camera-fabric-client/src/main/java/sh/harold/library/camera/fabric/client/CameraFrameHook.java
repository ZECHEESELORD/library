package sh.harold.library.camera.fabric.client;

import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.spatial.Vec3;

/**
 * Internal: the bridge the mixins call. Not API; signatures can change without notice.
 */
public final class CameraFrameHook {

    private CameraFrameHook() {
    }

    /**
     * Evaluates this frame's camera state for a first-person view of the local player, or returns {@code null} when
     * no service is open or the view is not one the rig drives. The caller applies only non-zero layer offsets, so an
     * idle rig leaves the camera exactly as vanilla built it.
     */
    public static CameraState frame(boolean drivable, double x, double y, double z, float yRot, float xRot,
                                    float fov, double eyeHeight, float partial) {
        FabricCameraService service = FabricClientCamera.current();
        if (service == null) {
            return null;
        }
        if (!drivable || !Float.isFinite(fov) || fov <= 0.0f) {
            service.skipFrame();
            return null;
        }
        return service.frame(new ViewInput(new Vec3(x, y, z), yRot, xRot, fov, eyeHeight), partial);
    }

    /**
     * Whether the last driven frame held an absolute eye height, so the host's eye-height easing should be held at
     * the body's eye. False when no service is open or the rig is not driving.
     */
    public static boolean holdsEyeHeight() {
        FabricCameraService service = FabricClientCamera.current();
        if (service == null) {
            return false;
        }
        CameraState state = service.frameState();
        return state != null && !Double.isNaN(state.layers().eyeTarget());
    }

    /** The look-input scale of the last driven frame: 1 when nothing scales look or the rig is not driving. */
    public static double lookScale() {
        FabricCameraService service = FabricClientCamera.current();
        if (service == null) {
            return 1.0;
        }
        CameraState state = service.frameState();
        return state == null ? 1.0 : state.lookInputScale();
    }
}
