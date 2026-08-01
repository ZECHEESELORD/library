package sh.harold.library.message.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.junit.jupiter.api.Test;
import sh.harold.library.message.Message;
import sh.harold.library.message.TitleMessage;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TitleMessageTest {

    @Test
    void titleUsesHouseTimingAndPreservesComponents() {
        Component title = Component.text("Tangleburg's Path", NamedTextColor.AQUA);
        Component subtitle = Component.text("NEW AREA DISCOVERED!", NamedTextColor.GOLD);
        TitleMessage message = Message.title(title, subtitle);

        assertEquals(title, message.title());
        assertEquals(subtitle, message.subtitle());
        assertEquals(Duration.ofMillis(500), message.fadeIn());
        assertEquals(Duration.ofMillis(2_500), message.stay());
        assertEquals(Duration.ofMillis(500), message.fadeOut());
    }

    @Test
    void timesCreatesAnIndependentMessageAndShowsThroughAudience() {
        TitleMessage original = Message.title(Component.text("TREE GIFT"));
        TitleMessage timed = original.times(
                Duration.ZERO,
                Duration.ofSeconds(3),
                Duration.ofMillis(750)
        );
        AtomicReference<Title> shown = new AtomicReference<>();
        Audience audience = new Audience() {
            @Override
            public void showTitle(Title title) {
                shown.set(title);
            }
        };

        timed.show(audience);

        assertNotSame(original, timed);
        assertEquals(Duration.ofMillis(500), original.fadeIn());
        assertEquals(Title.title(
                Component.text("TREE GIFT"),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(750))
        ), shown.get());
    }

    @Test
    void negativeTimingsFailFast() {
        TitleMessage message = Message.title(Component.text("Quest Complete"));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> message.times(Duration.ofMillis(-1), Duration.ZERO, Duration.ZERO)
        );

        assertEquals("fadeIn cannot be negative", failure.getMessage());
    }
}
