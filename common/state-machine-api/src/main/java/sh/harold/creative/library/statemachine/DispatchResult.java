package sh.harold.creative.library.statemachine;

import java.util.List;
import java.util.Objects;

public record DispatchResult<S extends Enum<S>, E, F>(
        E event,
        S previousState,
        S currentState,
        StateChange<S> stateChange,
        List<F> effects,
        List<TimerCommand<E>> timerCommands
) {

    public DispatchResult {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(stateChange, "stateChange");
        effects = List.copyOf(effects);
        timerCommands = List.copyOf(timerCommands);
    }
}
