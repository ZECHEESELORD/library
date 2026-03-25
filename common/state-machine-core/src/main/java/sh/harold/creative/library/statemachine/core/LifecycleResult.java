package sh.harold.creative.library.statemachine.core;

import sh.harold.creative.library.statemachine.TimerCommand;

import java.util.List;

public record LifecycleResult<E, F>(List<F> effects, List<TimerCommand<E>> timerCommands) {

    public LifecycleResult {
        effects = List.copyOf(effects);
        timerCommands = List.copyOf(timerCommands);
    }

    public static <E, F> LifecycleResult<E, F> empty() {
        return new LifecycleResult<>(List.of(), List.of());
    }

    public static <E, F> LifecycleResult<E, F> of(List<? extends F> effects, List<? extends TimerCommand<E>> timerCommands) {
        return new LifecycleResult<>(List.copyOf(effects), List.copyOf(timerCommands));
    }
}
