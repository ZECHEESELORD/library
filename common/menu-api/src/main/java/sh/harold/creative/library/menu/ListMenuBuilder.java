package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

public interface ListMenuBuilder {

    /**
     * House footer policy for v1 list menus:
     * slot 45 = previous page,
     * slot 48 = back,
     * slot 49 = close,
     * slot 53 = next,
     * remaining bottom-row slots = utility controls or black-pane filler.
     */

    ListMenuBuilder title(String title);

    ListMenuBuilder title(Component title);

    ListMenuBuilder utility(UtilitySlot slot, MenuItem item);

    ListMenuBuilder addItem(MenuItem item);

    ListMenuBuilder addItems(Iterable<? extends MenuItem> items);

    <T> ListMenuBuilder addItems(Iterable<T> items, java.util.function.Function<T, ? extends MenuItem> mapper);

    Menu build();
}
