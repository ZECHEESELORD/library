package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuStack;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

record MenuSessionView(Component title, List<MenuSlot> slots, MenuStack cursor, Set<Integer> reactiveClickTargets) {

    MenuSessionView {
        title = Objects.requireNonNull(title, "title");
        slots = List.copyOf(slots);
        reactiveClickTargets = Set.copyOf(reactiveClickTargets);
    }

    MenuSessionView(Component title, List<MenuSlot> slots, MenuStack cursor) {
        this(title, slots, cursor, Set.of());
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

    boolean acceptsReactiveClick(int slot) {
        return reactiveClickTargets.contains(slot);
    }
}
