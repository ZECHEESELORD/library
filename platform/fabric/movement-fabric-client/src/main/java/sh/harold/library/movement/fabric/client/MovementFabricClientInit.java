package sh.harold.library.movement.fabric.client;

import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint: registers the connection events once. The service itself opens when a game asks for it. */
public final class MovementFabricClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientMovement.installEvents();
    }
}
