package sh.harold.library.menu.fabric;

import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ReactiveMenu;
import sh.harold.library.menu.ReactiveMenuEffect;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveMenuResult;
import sh.harold.library.menu.ReactiveMenuView;
import sh.harold.library.menu.ReactiveTextPromptRequest;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.StandardMenuService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuPromptTickTest {

    @Test
    void promptSuspendsStateUpdatingTicksUntilTheMenuReopens() {
        AtomicInteger tickReductions = new AtomicInteger();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .tickEvery(1L)
                .render(value -> ReactiveMenuView.builder("Value " + value).build())
                .reduce((value, input) -> {
                    if (input instanceof ReactiveMenuInput.Tick) {
                        tickReductions.incrementAndGet();
                        int next = value + 1;
                        if (value == 0) {
                            return ReactiveMenuResult.update(next, new ReactiveMenuEffect.RequestTextPrompt(
                                    ReactiveTextPromptRequest.prompt("value", "Enter a value", "")));
                        }
                        return ReactiveMenuResult.update(next);
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        MenuSessionState state = new MenuSessionState(menu);
        state.currentFrame();

        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        AtomicReference<FabricMenuTickController> controller = new AtomicReference<>();
        AtomicBoolean promptActive = new AtomicBoolean();
        AtomicBoolean containerOpen = new AtomicBoolean(true);
        AtomicBoolean sessionActive = new AtomicBoolean(true);
        AtomicInteger unexpectedInventoryOpens = new AtomicInteger();
        AtomicInteger expectedReopens = new AtomicInteger();

        Runnable periodicTick = () -> {
            long beforeRevision = state.revision();
            List<ReactiveMenuEffect> effects = state.tick();
            boolean requestedPrompt = effects.stream()
                    .anyMatch(ReactiveMenuEffect.RequestTextPrompt.class::isInstance);
            if (requestedPrompt) {
                promptActive.set(true);
                containerOpen.set(false);
                controller.get().stop();
                return;
            }
            if (beforeRevision != state.revision() && !containerOpen.get()) {
                unexpectedInventoryOpens.incrementAndGet();
                sessionActive.set(false);
            }
        };
        controller.set(new FabricMenuTickController(scheduler.intervalScheduler(), periodicTick));
        controller.get().update(state.tickIntervalTicks());

        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        assertEquals(1, tickReductions.get());
        assertTrue(promptActive.get());
        assertFalse(containerOpen.get());
        assertTrue(sessionActive.get());
        assertEquals(0, unexpectedInventoryOpens.get());

        state.dispatchReactive(new ReactiveMenuInput.TextPromptSubmitted(
                "value", "complete", sh.harold.library.menu.ReactiveTextPromptMode.PROMPT));
        promptActive.set(false);
        containerOpen.set(true);
        expectedReopens.incrementAndGet();
        controller.get().update(state.tickIntervalTicks());
        scheduler.tick();
        scheduler.tick();

        assertEquals(3, tickReductions.get());
        assertFalse(promptActive.get());
        assertTrue(containerOpen.get());
        assertTrue(sessionActive.get());
        assertEquals(1, expectedReopens.get());
        assertEquals(0, unexpectedInventoryOpens.get());
    }
}
