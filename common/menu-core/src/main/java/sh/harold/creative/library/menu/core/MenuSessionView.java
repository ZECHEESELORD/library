package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuStack;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

record MenuSessionView(Component title, List<MenuSlot> slots, MenuStack cursor) {

    MenuSessionView {
        title = Objects.requireNonNull(title, "title");
        slots = List.copyOf(slots);
    }

    MenuFrame frame() {
        return new MenuFrame(title, slots);
    }

    Optional<MenuSlot> slot(int slot) {
        if (slot < 0 || slot >= slots.size()) {
            return Optional.empty();
        }
        return Optional.of(slots.get(slot));
    }
}
