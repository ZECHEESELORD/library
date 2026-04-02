package sh.harold.creative.library.menu;

@FunctionalInterface
public interface ReactiveTabsRenderer<S> {

    ReactiveTabsView render(S state);
}
