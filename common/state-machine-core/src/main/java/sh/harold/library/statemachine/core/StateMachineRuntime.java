package sh.harold.library.statemachine.core;

import sh.harold.library.statemachine.StateMachine;

public interface StateMachineRuntime<S extends Enum<S>, E, F> extends StateMachine<S, E, F>, AutoCloseable {

    void enqueue(E event);

    @Override
    void close();
}
