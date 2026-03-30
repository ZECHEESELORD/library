package sh.harold.creative.library.overlay.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.overlay.OverlayConflictPolicy;
import sh.harold.creative.library.overlay.ScreenOverlay;
import sh.harold.creative.library.overlay.ScreenOverlayHandle;
import sh.harold.creative.library.overlay.ScreenOverlayRequest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardScreenOverlayControllerTest {

    @Test
    void sameKeyReplacementInvalidatesStaleHandle() {
        StandardScreenOverlayController controller = new StandardScreenOverlayController();

        ScreenOverlayHandle first = controller.show(request("creative:flash", 0xFFFFFF, 1.0f, OverlayConflictPolicy.STACK, 0, 20, 0));
        ScreenOverlayHandle second = controller.show(request("creative:flash", 0xFF5555, 0.8f, OverlayConflictPolicy.STACK, 0, 20, 0));

        assertFalse(first.active());
        assertTrue(second.active());

        first.close();

        assertTrue(second.active());
        assertEquals(0xFF5555, controller.composite().rgb());
    }

    @Test
    void stackCompositesNewestOverlayOnTop() {
        StandardScreenOverlayController controller = new StandardScreenOverlayController();

        controller.show(request("creative:haze", 0x0000FF, 0.25f, OverlayConflictPolicy.STACK, 0, 20, 0));
        controller.show(request("creative:warning", 0xFF0000, 0.50f, OverlayConflictPolicy.STACK, 0, 20, 0));

        ScreenOverlayComposite composite = controller.composite();

        assertTrue(composite.visible());
        assertEquals(0xCC0033, composite.rgb());
        assertEquals(0.625f, composite.opacity(), 0.001f);
    }

    @Test
    void replaceAllClearsExistingOverlays() {
        StandardScreenOverlayController controller = new StandardScreenOverlayController();

        ScreenOverlayHandle haze = controller.show(request("creative:haze", 0x55FFFF, 0.4f, OverlayConflictPolicy.STACK, 0, 20, 0));
        ScreenOverlayHandle warning = controller.show(request("creative:warning", 0xFF5555, 0.9f, OverlayConflictPolicy.REPLACE_ALL, 0, 20, 0));

        assertFalse(haze.active());
        assertTrue(warning.active());
        assertEquals(0xFF5555, controller.composite().rgb());
    }

    @Test
    void fadeDurationsAdvanceAndExpireAutomatically() {
        StandardScreenOverlayController controller = new StandardScreenOverlayController();
        controller.show(request("creative:flash", 0xFFFFFF, 1.0f, OverlayConflictPolicy.STACK, 2, 1, 2));

        assertFalse(controller.composite().visible());

        controller.advance();
        ScreenOverlayComposite fadeIn = controller.composite();
        assertTrue(fadeIn.visible());
        assertEquals(0.5f, fadeIn.opacity(), 0.001f);

        controller.advance();
        assertEquals(1.0f, controller.composite().opacity(), 0.001f);

        controller.advance();
        assertEquals(1.0f, controller.composite().opacity(), 0.001f);

        controller.advance();
        assertEquals(0.5f, controller.composite().opacity(), 0.001f);

        controller.advance();
        assertFalse(controller.hasActiveOverlays());
        assertFalse(controller.composite().visible());
    }

    @Test
    void clearKeyAndClearAllRemoveOverlaysImmediately() {
        StandardScreenOverlayController controller = new StandardScreenOverlayController();
        ScreenOverlayHandle haze = controller.show(request("creative:haze", 0x55FFFF, 0.4f, OverlayConflictPolicy.STACK, 0, 20, 0));
        controller.show(request("creative:warning", 0xFF5555, 0.9f, OverlayConflictPolicy.STACK, 0, 20, 0));

        controller.clear(Key.key("creative", "warning"));
        assertTrue(haze.active());
        assertEquals(0x55FFFF, controller.composite().rgb());

        controller.clearAll();
        assertFalse(controller.hasActiveOverlays());
        assertFalse(controller.composite().visible());
    }

    @Test
    void closeRejectsFurtherMutation() {
        StandardScreenOverlayController controller = new StandardScreenOverlayController();
        controller.close();

        assertThrows(IllegalStateException.class, controller::advance);
        assertThrows(IllegalStateException.class, () -> controller.show(request("creative:flash", 0xFFFFFF, 1.0f, OverlayConflictPolicy.STACK, 0, 20, 0)));
    }

    private static ScreenOverlayRequest request(
            String key,
            int rgb,
            float opacity,
            OverlayConflictPolicy conflictPolicy,
            long fadeInTicks,
            long holdTicks,
            long fadeOutTicks
    ) {
        return new ScreenOverlayRequest(
                Key.key(key),
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
