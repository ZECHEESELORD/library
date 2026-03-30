package sh.harold.creative.library.example.paper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.overlay.OverlayConflictPolicy;
import sh.harold.creative.library.overlay.ScreenOverlay;
import sh.harold.creative.library.overlay.ScreenOverlayRequest;
import sh.harold.creative.library.overlay.paper.PaperScreenOverlayPlatform;

import java.time.Duration;

final class PaperScreenOverlayExamples {

    private static final Key HAZE = Key.key("creative", "demo/haze");
    private static final Key FLASH = Key.key("creative", "demo/flash");
    private static final Key WARNING = Key.key("creative", "demo/warning");

    private final JavaPlugin plugin;
    private final PaperScreenOverlayPlatform overlays;
    private final PaperExampleMessages feedback;

    PaperScreenOverlayExamples(JavaPlugin plugin, PaperScreenOverlayPlatform overlays, PaperExampleMessages feedback) {
        this.plugin = plugin;
        this.overlays = overlays;
        this.feedback = feedback;
    }

    void runDemo(Player player) {
        clear(player);
        feedback.info(player, "Running the overlay demo. Use {command} clear to stop it early.",
                Message.slot("command", feedback.command("/testoverlays")));

        overlays.show(player, request(HAZE, 0x4F6D8A, 0.36f, 15, 80, 30, OverlayConflictPolicy.STACK));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> showIfOnline(player, request(FLASH, 0xFFFFFF, 0.75f, 0, 2, 8, OverlayConflictPolicy.STACK)),
                20L);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> showIfOnline(player, request(FLASH, 0xFFF1C2, 0.95f, 0, 2, 10, OverlayConflictPolicy.STACK)),
                24L);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> showIfOnline(player, request(WARNING, 0xAA2A2A, 0.82f, 5, 35, 20, OverlayConflictPolicy.REPLACE_ALL)),
                60L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                clear(player);
            }
        }, 140L);
    }

    void clear(Player player) {
        overlays.clearAll(player);
    }

    private void showIfOnline(Player player, ScreenOverlayRequest request) {
        if (player.isOnline()) {
            overlays.show(player, request);
        }
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
