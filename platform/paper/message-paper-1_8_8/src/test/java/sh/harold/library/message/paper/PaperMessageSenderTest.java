package sh.harold.library.message.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperMessageSenderTest {
    @Test
    void ownsItsAudienceLifecycle() {
        assertTrue(AutoCloseable.class.isAssignableFrom(PaperMessageSender.class));
    }
}
