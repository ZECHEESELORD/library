package sh.harold.creative.library.statemachine;

@FunctionalInterface
public interface StateReducer<C, S extends Enum<S>, E, F> {

    ReducerResult<S, E, F> reduce(C context, E event);
}
