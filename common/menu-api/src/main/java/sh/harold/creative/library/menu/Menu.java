package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.Set;

public interface Menu extends MenuDefinition {

    Component title();

    String initialFrameId();

    Set<String> frameIds();

    MenuFrame frame(String frameId);

    default MenuFrame initialFrame() {
        return frame(initialFrameId());
    }
}
