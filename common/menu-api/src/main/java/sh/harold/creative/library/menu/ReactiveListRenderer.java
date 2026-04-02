package sh.harold.creative.library.menu;

@FunctionalInterface
public interface ReactiveListRenderer<S> {

    ReactiveListView render(S state);
}
