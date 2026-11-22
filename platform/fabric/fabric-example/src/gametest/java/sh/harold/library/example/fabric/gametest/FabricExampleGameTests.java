package sh.harold.library.example.fabric.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.fabric.FabricMenuPlatform;
import sh.harold.library.message.Message;
import sh.harold.library.message.fabric.FabricMessageSender;
import sh.harold.library.sound.CuePlayback;
import sh.harold.library.sound.SoundCueKeys;
import sh.harold.library.sound.fabric.FabricServerSoundCuePlatform;

public final class FabricExampleGameTests {

    private static final List<String> SERVER_MOD_IDS = List.of(
            "creative_library_message_fabric",
            "creative_library_sound_fabric",
            "creative_library_menu_fabric",
            "creative_library_fabric_example");

    @GameTest
    public void serverAdaptersAreLoaded(GameTestHelper helper) {
        for (String modId : SERVER_MOD_IDS) {
            helper.assertTrue(FabricLoader.getInstance().isModLoaded(modId), "Missing library mod " + modId);
        }
        helper.succeed();
    }

    @GameTest
    public void soundCuePlaysAtPosition(GameTestHelper helper) {
        FabricServerSoundCuePlatform sounds = new FabricServerSoundCuePlatform();
        try {
            Vec3 absolute = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
            sh.harold.library.spatial.Vec3 position =
                    new sh.harold.library.spatial.Vec3(absolute.x, absolute.y, absolute.z);
            CuePlayback playback = sounds.play(helper.getLevel(), position, SoundCueKeys.REWARD_DISCOVERY);
            helper.assertTrue(playback != null, "Sound cue playback was null");
            playback.cancel();
        } finally {
            sounds.close();
        }
        helper.succeed();
    }

    @GameTest
    @SuppressWarnings("removal") // The mock in-level player is still the simplest real ServerPlayer on 26.3.
    public void menuOpensForServerPlayer(GameTestHelper helper) {
        FabricMenuPlatform menus = new FabricMenuPlatform();
        FabricMessageSender messages = new FabricMessageSender();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        messages.send(player, Message.info("Opening the gametest menu."));
        Menu menu = menus.list()
                .title("Gametest Menu")
                .addItem(MenuButton.builder(MenuIcon.vanilla("book"))
                        .name("Gametest Button")
                        .action(ActionVerb.VIEW, context -> { })
                        .build())
                .build();
        menus.open(player, menu);
        helper.succeedWhen(() -> {
            helper.assertTrue(player.containerMenu != player.inventoryMenu,
                    "Library menu did not open; player still has the inventory menu");
            menus.close();
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }
}
