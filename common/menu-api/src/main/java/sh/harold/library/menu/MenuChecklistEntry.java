package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

import java.util.Objects;

public record MenuChecklistEntry(Component label, boolean complete) {

    public MenuChecklistEntry {
        label = MenuComponents.component(Objects.requireNonNull(label, "label"));
        MenuComponents.requireContent(label, "label");
    }

    public static MenuChecklistEntry complete(String label) {
        return complete(Component.text(MenuComponents.requireText(label, "label")));
    }

    public static MenuChecklistEntry complete(ComponentLike label) {
        return new MenuChecklistEntry(Objects.requireNonNull(label, "label").asComponent(), true);
    }

    public static MenuChecklistEntry incomplete(String label) {
        return incomplete(Component.text(MenuComponents.requireText(label, "label")));
    }

    public static MenuChecklistEntry incomplete(ComponentLike label) {
        return new MenuChecklistEntry(Objects.requireNonNull(label, "label").asComponent(), false);
    }
}
