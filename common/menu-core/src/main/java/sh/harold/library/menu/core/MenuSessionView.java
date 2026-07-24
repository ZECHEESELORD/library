package sh.harold.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuSlot;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

record MenuSessionView(Component title, List<MenuSlot> slots, Set<Integer> reactiveClickTargets) {

    MenuSessionView {
        title = Objects.requireNonNull(title, "title");
        slots = List.copyOf(slots);
        reactiveClickTargets = Set.copyOf(reactiveClickTargets);
    }

    MenuSessionView(Component title, List<MenuSlot> slots) {
        this(title, slots, Set.of());
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
