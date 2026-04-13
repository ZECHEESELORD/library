package sh.harold.creative.library.example.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.fabric.client.FabricClientMessageSender;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.fabric.client.FabricClientSoundCuePlatform;

public final class FabricClientExampleMod implements ClientModInitializer {

    private final FabricClientMessageSender messages = new FabricClientMessageSender();
    private final FabricClientSoundCuePlatform sounds = new FabricClientSoundCuePlatform();
    private boolean bootstrapped;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (bootstrapped || client.player == null) {
                return;
            }
            bootstrapped = true;
            messages.sendToClient(Message.info("Fabric client example ready."));
            sounds.play(client.player, SoundCueKeys.MENU_CLICK);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> sounds.close());
    }
}
