package sh.harold.creative.library.overlay.minestom;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinestomFutureGuardTest {

    @Test
    void helperFailsFastForIncompleteFutures() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                MinestomFutureGuard.requireCompleted(new CompletableFuture<>(), "screen overlay shell spawn")
        );
        assertEquals("screen overlay shell spawn must already be complete on the Minestom owned thread; blocking is not allowed", exception.getMessage());
    }
}
