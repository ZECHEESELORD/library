package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuGeometry;

import java.util.Map;

record StandardMenu(Component title, MenuGeometry geometry, int rows, String initialFrameId, Map<String, MenuFrame> frames) implements Menu {

    StandardMenu {
        title = java.util.Objects.requireNonNull(title, "title");
        frames = Map.copyOf(frames);
    }
}
