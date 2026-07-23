package sh.harold.library.menu.fabric;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FabricMenuPromptFailureTest {

    @Test
    void reducerFailureQuarantinesTheRegisteredPromptSession() {
        AtomicBoolean registered = new AtomicBoolean(true);
        AtomicInteger quarantines = new AtomicInteger();

        FabricMenuRuntime.runPromptCompletionPhase(
                () -> {
                    throw new IllegalStateException("reducer failed");
                },
                () -> {
                    registered.set(false);
                    quarantines.incrementAndGet();
                });

        assertFalse(registered.get());
        assertEquals(1, quarantines.get());
    }

    @Test
    void scheduledApplyAndReopenFailuresEachQuarantine() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        AtomicInteger quarantines = new AtomicInteger();
        AtomicInteger reopenAttempts = new AtomicInteger();

        scheduler.scheduleNextTick(() -> FabricMenuRuntime.runPromptCompletionPhase(
                () -> FabricMenuRuntime.applyPromptCompletionEffects(
                        () -> {
                            throw new IllegalStateException("effect failed");
                        },
                        reopenAttempts::incrementAndGet),
                quarantines::incrementAndGet));
        scheduler.scheduleNextTick(() -> FabricMenuRuntime.runPromptCompletionPhase(
                () -> FabricMenuRuntime.applyPromptCompletionEffects(
                        () -> false,
                        () -> {
                            reopenAttempts.incrementAndGet();
                            throw new IllegalStateException("reopen failed");
                        }),
                quarantines::incrementAndGet));

        scheduler.tick();

        assertEquals(2, quarantines.get());
        assertEquals(1, reopenAttempts.get());
    }

    @Test
    void scheduledActivationFailureTearsDownPromptAndSession() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        AtomicBoolean promptRegistered = new AtomicBoolean(true);
        AtomicBoolean sessionRegistered = new AtomicBoolean(true);

        scheduler.scheduleNextTick(() -> FabricMenuRuntime.runPromptActivationPhase(
                () -> {
                    throw new IllegalStateException("prompt activation failed");
                },
                () -> {
                    promptRegistered.set(false);
                    sessionRegistered.set(false);
                }));

        scheduler.tick();

        assertFalse(promptRegistered.get());
        assertFalse(sessionRegistered.get());
    }
}
