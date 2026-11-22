package sh.harold.library.example.fabric.client.gametest;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricClientExampleGameTests implements FabricClientGameTest {

    private static final List<String> LIBRARY_MOD_IDS = List.of(
            "creative_library_data_fabric",
            "creative_library_entity_fabric",
            "creative_library_entity_fabric_client",
            "creative_library_menu_fabric",
            "creative_library_menu_fabric_client",
            "creative_library_message_fabric",
            "creative_library_message_fabric_client",
            "creative_library_sound_fabric",
            "creative_library_sound_fabric_client",
            "creative_library_fabric_example",
            "creative_library_fabric_client_example");

    private static final String SERVER_WELCOME = "Welcome to the Fabric example!";
    private static final int WELCOME_TIMEOUT_TICKS = 200;

    @Override
    public void runTest(ClientGameTestContext context) {
        for (String modId : LIBRARY_MOD_IDS) {
            if (!FabricLoader.getInstance().isModLoaded(modId)) {
                throw new AssertionError("Missing library mod " + modId);
            }
        }

        // The server example greets joining players through FabricMessageSender; watch for it on the client.
        AtomicBoolean welcomed = new AtomicBoolean();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && message.getString().contains(SERVER_WELCOME)) {
                welcomed.set(true);
            }
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> welcomed.get(), WELCOME_TIMEOUT_TICKS);
            world.getConnection().waitForChunksRender();
            Path screenshot = context.takeScreenshot("library_fabric_singleplayer");
            System.out.println("SCREENSHOT " + screenshot.toAbsolutePath());
        }
    }
}
