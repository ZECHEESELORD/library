package sh.harold.library.menu;

@FunctionalInterface
public interface ReactiveTabsRenderer<S> {

    ReactiveTabsView render(S state);
}
