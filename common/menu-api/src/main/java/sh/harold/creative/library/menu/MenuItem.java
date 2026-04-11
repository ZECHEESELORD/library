package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public sealed interface MenuItem permits MenuButton, MenuDisplayItem, MenuStack {

    MenuIcon icon();

    Component name();

    Optional<String> secondary();

    List<MenuBlock> blocks();

    default Optional<List<Component>> exactLore() {
        return Optional.empty();
    }

    boolean glow();

    default int amount() {
        return 1;
    }

    default Map<MenuClick, MenuInteraction> interactions() {
        return Map.of();
    }

    default boolean promptSuppressed() {
        return false;
    }
}
