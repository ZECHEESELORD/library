package sh.harold.creative.library.statemachine;

public interface StateMachine<S extends Enum<S>, E, F> {

    S currentState();

    DispatchResult<S, E, F> dispatch(E event);
}
