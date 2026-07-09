package sh.harold.library.menu;

import net.kyori.adventure.text.Component;

public interface ConfirmationMenuBuilder {

    ConfirmationMenuBuilder title(String title);

    ConfirmationMenuBuilder title(Component title);

    ConfirmationMenuBuilder info(MenuDisplayItem info);

    ConfirmationMenuBuilder cancel(MenuButton cancel);

    ConfirmationMenuBuilder confirm(MenuButton confirm);

    Menu build();
}
