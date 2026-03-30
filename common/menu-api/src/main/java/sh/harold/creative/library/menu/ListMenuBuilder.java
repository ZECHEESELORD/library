package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

public interface ListMenuBuilder {

    /**
     * House footer policy for v1 list menus:
     * centered content panel = slots 10-16, 19-25, 28-34, and 37-43,
     * that 7x4 interior stays open by default when no item is authored,
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
