package sh.harold.library.example.feel;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.key.Key;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.fabric.ServerMovement;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The feel example's server side: opens the movement service with all five stock moves at default tuning when a
 * server starts (hot-reloading {@code config/creative_library_movement/}) and closes it when the server stops. The
 * static switches change the live moveset and push it to every client, with no levers or commands, so gametests
 * and other code can flip them.
 */
public final class FeelExample implements ModInitializer {

    /** The hot-reloaded tuning folder under the game's config directory. */
    public static final String TUNING_FOLDER = "creative_library_movement";

    private static ServerMovement movement;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Path tuning = FabricLoader.getInstance().getConfigDir().resolve(TUNING_FOLDER);
            movement = ServerMovement.open(tuning);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (movement != null) {
                movement.close();
                movement = null;
            }
        });
    }

    /** The open server service, while a server runs. */
    public static Optional<ServerMovement> movement() {
        return Optional.ofNullable(movement).filter(ServerMovement::active);
    }

    /** Variable jump preset A, B or C. */
    public static void setJumpPreset(JumpPreset preset) {
        require().setJumpPreset(preset);
    }

    /** Slide variant S1 or S2. */
    public static void setSlideVariant(SlideVariant variant) {
        require().setSlideVariant(variant);
    }

    /** Turns one move on or off (keys in {@link sh.harold.library.movement.MoveKeys}). */
    public static void setMove(Key move, boolean enabled) {
        require().setEnabled(move, enabled);
    }

    private static ServerMovement require() {
        return movement().orElseThrow(() -> new IllegalStateException("no server is running"));
    }
}
