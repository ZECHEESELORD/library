package sh.harold.creative.library.statemachine.core;

@FunctionalInterface
public interface StateLifecycleAction<C, E, F> {

    LifecycleResult<E, F> run(C context);
}
