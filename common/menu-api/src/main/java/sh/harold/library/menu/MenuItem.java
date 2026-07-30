package sh.harold.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public sealed interface MenuItem permits MenuButton, MenuDisplayItem, MenuStack {

    MenuIcon icon();

    Component name();

    Optional<Component> secondary();

    List<MenuSection> sections();

    default List<Component> statusLines() {
        return List.of();
    }

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

    default MenuTooltipBehavior tooltipBehavior() {
        return MenuTooltipBehavior.CHROME;
    }
}
