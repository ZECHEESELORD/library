package sh.harold.library.movement.fabric;

import net.fabricmc.api.ModInitializer;
import sh.harold.library.movement.fabric.net.MovementPayloads;

/** Entrypoint on both sides: registers the movement payload types and the server hooks once per JVM. */
public final class MovementFabricInit implements ModInitializer {

    @Override
    public void onInitialize() {
        MovementPayloads.registerTypes();
        ServerMovement.installHooks();
    }
}
