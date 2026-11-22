package sh.harold.library.example.feel.client;

import net.fabricmc.api.ClientModInitializer;
import sh.harold.library.camera.fabric.client.FabricClientCamera;
import sh.harold.library.camera.rig.IntensityProfile;
import sh.harold.library.movement.fabric.client.ClientMovement;

/**
 * The feel example's client side: opens the client movement service at init, so the movement feature is in this
 * client's handshake and the moves predict as soon as the server sends its moveset.
 */
public final class FeelExampleClient implements ClientModInitializer {

    private static ClientMovement movement;

    @Override
    public void onInitializeClient() {
        movement = ClientMovement.open();
    }

    public static ClientMovement movement() {
        return movement;
    }

    /** The Full or Reduced camera intensity switch (saved, and applied from the next frame). */
    public static void setCameraIntensity(IntensityProfile profile) {
        FabricClientCamera.setIntensitySetting(profile);
    }
}
