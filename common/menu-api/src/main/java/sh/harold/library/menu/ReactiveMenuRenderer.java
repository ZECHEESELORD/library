package sh.harold.library.menu;

@FunctionalInterface
public interface ReactiveMenuRenderer<S> {

    ReactiveMenuView render(S state);
}
