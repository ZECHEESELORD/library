package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.overlay.OverlayConflictPolicy;
import sh.harold.creative.library.overlay.ScreenOverlay;
import sh.harold.creative.library.overlay.ScreenOverlayRequest;
import sh.harold.creative.library.overlay.minestom.MinestomScreenOverlayPlatform;

import java.time.Duration;

final class MinestomScreenOverlayExamples {

    private static final Key HAZE = Key.key("creative", "demo/haze");
    private static final Key FLASH = Key.key("creative", "demo/flash");
    private static final Key WARNING = Key.key("creative", "demo/warning");

    private final MinestomScreenOverlayPlatform overlays;
    private final MinestomDevHarnessMessages feedback;

    MinestomScreenOverlayExamples(MinestomScreenOverlayPlatform overlays, MinestomDevHarnessMessages feedback) {
        this.overlays = overlays;
        this.feedback = feedback;
    }

    void runDemo(Player player) {
        clear(player);
        feedback.info(player, "Running the overlay demo. Use {command} clear to stop it early.",
                Message.slot("command", feedback.command("/testoverlays")));

        overlays.show(player, request(HAZE, 0x4F6D8A, 0.36f, 15, 80, 30, OverlayConflictPolicy.STACK));
        schedule(20L, () -> showIfOnline(player, request(FLASH, 0xFFFFFF, 0.75f, 0, 2, 8, OverlayConflictPolicy.STACK)));
        schedule(24L, () -> showIfOnline(player, request(FLASH, 0xFFF1C2, 0.95f, 0, 2, 10, OverlayConflictPolicy.STACK)));
        schedule(60L, () -> showIfOnline(player, request(WARNING, 0xAA2A2A, 0.82f, 5, 35, 20, OverlayConflictPolicy.REPLACE_ALL)));
        schedule(140L, () -> {
            if (player.isOnline()) {
                clear(player);
            }
        });
    }

    void clear(Player player) {
        overlays.clearAll(player);
    }

    private void showIfOnline(Player player, ScreenOverlayRequest request) {
        if (player.isOnline()) {
            overlays.show(player, request);
        }
    }

    private static void schedule(long delayTicks, Runnable action) {
        MinecraftServer.getSchedulerManager().scheduleTask(
                action,
                delayTicks == 0L ? TaskSchedule.immediate() : TaskSchedule.tick(Math.toIntExact(delayTicks)),
                TaskSchedule.stop()
        );
    }

    private static ScreenOverlayRequest request(
            Key key,
            int rgb,
            float opacity,
            long fadeInTicks,
            long holdTicks,
            long fadeOutTicks,
            OverlayConflictPolicy conflictPolicy
    ) {
        return new ScreenOverlayRequest(
                key,
                new ScreenOverlay(
                        TextColor.color(rgb),
                        opacity,
                        Duration.ofMillis(fadeInTicks * 50L),
                        Duration.ofMillis(holdTicks * 50L),
                        Duration.ofMillis(fadeOutTicks * 50L),
                        conflictPolicy
                )
        );
    }
}
