package sh.harold.creative.library.statemachine.core;

import sh.harold.creative.library.statemachine.StateReducer;

import java.util.Objects;

public final class StateDefinitionBuilder<C, S extends Enum<S>, E, F> {

    private StateReducer<C, S, E, F> reducer;
    private StateLifecycleAction<C, E, F> onEnter = ignored -> LifecycleResult.empty();
    private StateLifecycleAction<C, E, F> onExit = ignored -> LifecycleResult.empty();

    public StateDefinitionBuilder<C, S, E, F> reducer(StateReducer<C, S, E, F> reducer) {
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        return this;
    }

    public StateDefinitionBuilder<C, S, E, F> onEnter(StateLifecycleAction<C, E, F> onEnter) {
        this.onEnter = Objects.requireNonNull(onEnter, "onEnter");
        return this;
    }

    public StateDefinitionBuilder<C, S, E, F> onExit(StateLifecycleAction<C, E, F> onExit) {
        this.onExit = Objects.requireNonNull(onExit, "onExit");
        return this;
    }

    StateMachineDefinition.StateNode<C, S, E, F> build() {
        if (reducer == null) {
            throw new IllegalStateException("State reducer is required");
        }
        return new StateMachineDefinition.StateNode<>(reducer, onEnter, onExit);
    }
}
