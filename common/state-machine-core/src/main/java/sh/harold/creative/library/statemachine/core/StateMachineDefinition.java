package sh.harold.creative.library.statemachine.core;

import sh.harold.creative.library.statemachine.StateReducer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class StateMachineDefinition<C, S extends Enum<S>, E, F> {

    private final S initialState;
    private final Map<S, StateNode<C, S, E, F>> states;

    StateMachineDefinition(S initialState, Map<S, StateNode<C, S, E, F>> states) {
        this.initialState = Objects.requireNonNull(initialState, "initialState");
        this.states = Map.copyOf(new LinkedHashMap<>(states));
        if (!this.states.containsKey(initialState)) {
            throw new IllegalArgumentException("Initial state is not defined: " + initialState);
        }
    }

    public static <C, S extends Enum<S>, E, F> StateMachineDefinitionBuilder<C, S, E, F> builder(S initialState) {
        return new StateMachineDefinitionBuilder<>(initialState);
    }

    public S initialState() {
        return initialState;
    }

    public StateMachineRuntime<S, E, F> createRuntime(C context, StateMachineScheduler scheduler) {
        return createRuntime(context, scheduler, effect -> {
        });
    }

    public StateMachineRuntime<S, E, F> createRuntime(C context, StateMachineScheduler scheduler, Consumer<? super F> effectSink) {
        return new SerialStateMachineRuntime<>(this, context, scheduler, effectSink);
    }

    StateNode<C, S, E, F> state(S state) {
        StateNode<C, S, E, F> stateNode = states.get(state);
        if (stateNode == null) {
            throw new IllegalArgumentException("Unknown state: " + state);
        }
        return stateNode;
    }

    record StateNode<C, S extends Enum<S>, E, F>(
            StateReducer<C, S, E, F> reducer,
            StateLifecycleAction<C, E, F> onEnter,
            StateLifecycleAction<C, E, F> onExit
    ) {

        StateNode {
            Objects.requireNonNull(reducer, "reducer");
            Objects.requireNonNull(onEnter, "onEnter");
            Objects.requireNonNull(onExit, "onExit");
        }
    }
}
