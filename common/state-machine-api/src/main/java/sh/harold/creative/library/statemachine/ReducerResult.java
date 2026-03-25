package sh.harold.creative.library.statemachine;

import java.util.List;
import java.util.Objects;

public record ReducerResult<S extends Enum<S>, E, F>(
        StateChange<S> stateChange,
        List<F> effects,
        List<TimerCommand<E>> timerCommands
) {

    public ReducerResult {
        Objects.requireNonNull(stateChange, "stateChange");
        effects = List.copyOf(effects);
        timerCommands = List.copyOf(timerCommands);
    }

    public static <S extends Enum<S>, E, F> ReducerResult<S, E, F> stay() {
        return new ReducerResult<>(StateChange.<S>stay(), List.of(), List.of());
    }

    public static <S extends Enum<S>, E, F> ReducerResult<S, E, F> stay(List<? extends F> effects, List<? extends TimerCommand<E>> timerCommands) {
        return new ReducerResult<>(StateChange.<S>stay(), List.copyOf(effects), List.copyOf(timerCommands));
    }

    public static <S extends Enum<S>, E, F> ReducerResult<S, E, F> move(S state) {
        return new ReducerResult<>(StateChange.move(state), List.of(), List.of());
    }

    public static <S extends Enum<S>, E, F> ReducerResult<S, E, F> move(
            S state,
            List<? extends F> effects,
            List<? extends TimerCommand<E>> timerCommands
    ) {
        return new ReducerResult<>(StateChange.move(state), List.copyOf(effects), List.copyOf(timerCommands));
    }
}
