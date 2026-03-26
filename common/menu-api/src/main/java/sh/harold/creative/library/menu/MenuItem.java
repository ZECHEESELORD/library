package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;

public sealed interface MenuItem permits MenuButton, MenuDisplayItem {

    MenuIcon icon();

    Component name();

    Optional<String> secondary();

    List<MenuBlock> blocks();

    boolean glow();
}
