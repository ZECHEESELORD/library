package sh.harold.creative.library.statemachine.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class StateMachineDefinitionBuilder<C, S extends Enum<S>, E, F> {

    private final S initialState;
    private final Map<S, StateMachineDefinition.StateNode<C, S, E, F>> states = new LinkedHashMap<>();

    StateMachineDefinitionBuilder(S initialState) {
        this.initialState = Objects.requireNonNull(initialState, "initialState");
    }

    public StateMachineDefinitionBuilder<C, S, E, F> state(S state, Consumer<StateDefinitionBuilder<C, S, E, F>> configurer) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(configurer, "configurer");
        if (states.containsKey(state)) {
            throw new IllegalArgumentException("State already defined: " + state);
        }
        StateDefinitionBuilder<C, S, E, F> builder = new StateDefinitionBuilder<>();
        configurer.accept(builder);
        states.put(state, builder.build());
        return this;
    }

    public StateMachineDefinition<C, S, E, F> build() {
        return new StateMachineDefinition<>(initialState, states);
    }
}
