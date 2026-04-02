package sh.harold.creative.library.menu;

public interface MenuService {

    ListMenuBuilder list();

    TabsMenuBuilder tabs();

    CanvasMenuBuilder canvas();

    ReactiveMenuBuilder<Void> reactive();

    ReactiveCanvasMenuBuilder<Void> reactiveCanvas();

    ReactiveListMenuBuilder<Void> reactiveList();

    ReactiveTabsMenuBuilder<Void> reactiveTabs();
}
