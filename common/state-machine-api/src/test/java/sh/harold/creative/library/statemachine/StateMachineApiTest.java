package sh.harold.creative.library.statemachine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateMachineApiTest {

    @Test
    void timerKeyRejectsBlankValues() {
        assertThrows(NullPointerException.class, () -> new TimerKey(null));
        assertThrows(IllegalArgumentException.class, () -> new TimerKey(" "));
    }

    @Test
    void reducerAndDispatchResultsDefensivelyCopyCollections() {
        List<String> effects = new ArrayList<>(List.of("one"));
        List<TimerCommand<String>> timers = new ArrayList<>(List.of(
                TimerCommand.schedule(new TimerKey("timeout"), Duration.ofSeconds(5), "timeout")
        ));

        ReducerResult<TestState, String, String> reducerResult = ReducerResult.move(TestState.ACTIVE, effects, timers);
        DispatchResult<TestState, String, String> dispatchResult = new DispatchResult<>(
                "event",
                TestState.IDLE,
                TestState.ACTIVE,
                StateChange.move(TestState.ACTIVE),
                effects,
                timers
        );

        effects.add("two");
        timers.add(TimerCommand.cancel(new TimerKey("other")));

        assertEquals(List.of("one"), reducerResult.effects());
        assertEquals(1, reducerResult.timerCommands().size());
        assertEquals(List.of("one"), dispatchResult.effects());
        assertEquals(1, dispatchResult.timerCommands().size());
        assertThrows(UnsupportedOperationException.class, () -> reducerResult.effects().add("three"));
        assertThrows(UnsupportedOperationException.class, () -> dispatchResult.timerCommands().clear());
    }

    private enum TestState {
        IDLE,
        ACTIVE
    }
}
