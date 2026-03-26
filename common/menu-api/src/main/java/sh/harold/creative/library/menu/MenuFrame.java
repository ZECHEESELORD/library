package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public record MenuFrame(Component title, List<MenuSlot> slots) {

    public MenuFrame {
        title = Objects.requireNonNull(title, "title");
        slots = List.copyOf(slots);
    }
}
