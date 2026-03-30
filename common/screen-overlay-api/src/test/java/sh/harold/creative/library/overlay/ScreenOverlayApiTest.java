package sh.harold.creative.library.overlay;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenOverlayApiTest {

    @Test
    void screenOverlayRejectsOpacityOutsideRange() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenOverlay(
                TextColor.color(0x55FFFF),
                1.1f,
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ZERO,
                OverlayConflictPolicy.STACK
        ));
        assertThrows(IllegalArgumentException.class, () -> new ScreenOverlay(
                TextColor.color(0x55FFFF),
                -0.1f,
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ZERO,
                OverlayConflictPolicy.STACK
        ));
    }

    @Test
    void screenOverlayRejectsNegativeDurations() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenOverlay(
                TextColor.color(0x55FFFF),
                0.5f,
                Duration.ofMillis(-1),
                Duration.ZERO,
                Duration.ZERO,
                OverlayConflictPolicy.STACK
        ));
    }

    @Test
    void requestRequiresKeyAndOverlay() {
        ScreenOverlay overlay = new ScreenOverlay(
                TextColor.color(0x55FFFF),
                0.75f,
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ZERO,
                OverlayConflictPolicy.REPLACE_ALL
        );

        ScreenOverlayRequest request = assertDoesNotThrow(() -> new ScreenOverlayRequest(Key.key("creative", "flash"), overlay));

        assertEquals(Key.key("creative", "flash"), request.key());
    }
}
