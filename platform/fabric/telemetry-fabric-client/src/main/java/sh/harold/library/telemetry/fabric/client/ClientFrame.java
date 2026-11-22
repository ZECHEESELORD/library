package sh.harold.library.telemetry.fabric.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * What a client telemetry column reads: the client, the local player, the main camera and the render partial
 * tick that positions should be interpolated with. On per-tick rows {@code partial} is {@code 1}, so
 * interpolated values equal the state the tick just produced; on per-frame rows it is the frame's partial.
 * Camera values are whatever the last rendered frame set.
 */
public record ClientFrame(Minecraft client, LocalPlayer player, Camera camera, float partial) {
}
