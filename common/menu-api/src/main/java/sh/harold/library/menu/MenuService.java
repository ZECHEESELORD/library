package sh.harold.library.menu;

public interface MenuService {

    ListMenuBuilder list();

    TabsMenuBuilder tabs();

    CanvasMenuBuilder canvas();

    default ConfirmationMenuBuilder confirmation() {
        throw new UnsupportedOperationException("Confirmation menus are not supported by this service");
    }

    ReactiveMenuBuilder<Void> reactive();

    ReactiveCanvasMenuBuilder<Void> reactiveCanvas();

    ReactiveListMenuBuilder<Void> reactiveList();

    ReactiveTabsMenuBuilder<Void> reactiveTabs();
}
