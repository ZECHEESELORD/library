package sh.harold.creative.library.menu;

public interface MenuService {

    ListMenuBuilder list();

    TabsMenuBuilder tabs();

    CanvasMenuBuilder canvas();

    ReactiveMenuBuilder<Void> reactive();
}
