package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MenuSlot(
        int slot,
        MenuIcon icon,
        Component title,
        List<Component> lore,
        boolean glow,
        Map<MenuClick, MenuInteraction> interactions
) {

    public MenuSlot {
        if (slot < 0 || slot > 53) {
            throw new IllegalArgumentException("slot must be between 0 and 53");
        }
        icon = Objects.requireNonNull(icon, "icon");
        title = Objects.requireNonNull(title, "title");
        lore = List.copyOf(lore);
        interactions = copyInteractions(interactions);
    }

    public boolean clickable() {
        return !interactions.isEmpty();
    }

    private static Map<MenuClick, MenuInteraction> copyInteractions(Map<MenuClick, MenuInteraction> interactions) {
        Objects.requireNonNull(interactions, "interactions");
        LinkedHashMap<MenuClick, MenuInteraction> copy = new LinkedHashMap<>();
        for (Map.Entry<MenuClick, MenuInteraction> entry : interactions.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "interaction key"),
                    Objects.requireNonNull(entry.getValue(), "interaction value"));
        }
        return Map.copyOf(copy);
    }
}
