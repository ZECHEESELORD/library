package sh.harold.library.camera.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import sh.harold.library.camera.rig.core.CameraColumns;
import sh.harold.library.telemetry.fabric.client.FabricClientTelemetry;

/** Registers the camera's telemetry columns. They read the last driven frame and write {@code NaN} otherwise. */
public final class CameraFabricClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CameraColumns.register(FabricClientTelemetry.registry(), (frame, time) -> {
            FabricCameraService service = FabricClientCamera.current();
            return service == null ? null : service.frameState();
        });
    }
}
