package sh.harold.creative.library.menu;

@FunctionalInterface
public interface ReactiveMenuRenderer<S> {

    ReactiveMenuView render(S state);
}
