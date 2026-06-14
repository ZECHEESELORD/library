package sh.harold.library.statemachine.core;

@FunctionalInterface
public interface StateLifecycleAction<C, E, F> {

    LifecycleResult<E, F> run(C context);
}
