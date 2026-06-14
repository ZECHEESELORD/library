package sh.harold.library.menu;

@FunctionalInterface
public interface ReactiveListRenderer<S> {

    ReactiveListView render(S state);
}
