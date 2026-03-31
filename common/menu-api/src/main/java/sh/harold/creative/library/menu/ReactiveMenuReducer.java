package sh.harold.creative.library.menu;

@FunctionalInterface
public interface ReactiveMenuReducer<S> {

    ReactiveMenuResult<S> reduce(S state, ReactiveMenuInput input);
}
