package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuGeometry;

import java.util.Map;

record StandardMenu(MenuGeometry geometry, int rows, String initialFrameId, Map<String, MenuFrame> frames) implements Menu {

    StandardMenu {
        frames = Map.copyOf(frames);
    }
}
