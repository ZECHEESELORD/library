package sh.harold.library.camera.fabric.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import sh.harold.library.camera.rig.CameraRigSettings;
import sh.harold.library.camera.rig.CameraService;
import sh.harold.library.camera.rig.IntensityProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The first-person camera rig on the Fabric client. {@link #open()} returns the one live {@link CameraService}; cues
 * started on it drive the vanilla camera every rendered frame (additive pitch and roll, FOV, eye height, shake) and
 * scale mouse look. With nothing live the camera is vanilla, untouched.
 *
 * <p><b>Time.</b> The rig ticks at the start of each unpaused client tick, and a frame drawn after tick {@code n} is
 * evaluated at rig time {@code n - 1 + partial}, the same timeline as frame telemetry.
 *
 * <p><b>Scope.</b> Layers apply only to the first-person view of the local player: not when the camera is detached
 * (third person), panoramic, or spectating another entity. The player's real rotation is never changed; additive pitch
 * and roll exist only in the rendered view.
 *
 * <p><b>Intensity.</b> Full or Reduced is a client option saved in {@code config/creative_library_camera.properties};
 * {@link #setIntensitySetting(IntensityProfile)} changes it (tests use it to flip profiles).
 *
 * <p>The Fabric events are registered once and route to the open service. Disconnecting ends live cues and trauma;
 * client shutdown closes the service. Use from the client thread.
 */
public final class FabricClientCamera {

    private static final AtomicBoolean EVENTS = new AtomicBoolean();
    private static volatile FabricCameraService active;

    private FabricClientCamera() {
    }

    /** Opens the camera service with default settings. Only one service is open at a time. */
    public static CameraService open() {
        return open(CameraRigSettings.DEFAULT);
    }

    /**
     * Opens the camera service. Closing it returns the camera to vanilla and frees the slot.
     *
     * @throws IllegalStateException if a service is already open
     */
    public static synchronized CameraService open(CameraRigSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (active != null && active.active()) {
            throw new IllegalStateException("a camera service is already open; close it first");
        }
        installEvents();
        FabricCameraService service = new FabricCameraService(settings, FabricClientCamera::release);
        service.setIntensity(CameraOptions.intensity());
        active = service;
        return service;
    }

    /** The open service, if any. */
    public static Optional<CameraService> active() {
        return Optional.ofNullable(active);
    }

    /** The saved Full or Reduced option. */
    public static IntensityProfile intensitySetting() {
        return CameraOptions.intensity();
    }

    /** Changes and saves the Full or Reduced option, and applies it to the open service from the next frame. */
    public static void setIntensitySetting(IntensityProfile profile) {
        CameraOptions.setIntensity(Objects.requireNonNull(profile, "profile"));
        FabricCameraService service = active;
        if (service != null) {
            service.setIntensity(profile);
        }
    }

    static FabricCameraService current() {
        return active;
    }

    private static synchronized void release(FabricCameraService service) {
        if (active == service) {
            active = null;
        }
    }

    private static void installEvents() {
        if (!EVENTS.compareAndSet(false, true)) {
            return;
        }
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            FabricCameraService service = active;
            if (service != null && client.level != null && !client.isPaused()) {
                service.rig().tick();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            FabricCameraService service = active;
            if (service != null) {
                service.rig().clearCues();
            }
        }));
        ClientLifecycleEvents.CLIENT_STOPPING.register(FabricClientCamera::stopping);
    }

    private static void stopping(Minecraft client) {
        FabricCameraService service = active;
        if (service != null) {
            service.close();
        }
    }
}
