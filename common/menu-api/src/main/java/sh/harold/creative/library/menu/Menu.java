package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.Map;

public interface Menu {

    MenuGeometry geometry();

    int rows();

    Component title();

    String initialFrameId();

    Map<String, MenuFrame> frames();

    default MenuFrame initialFrame() {
        return frames().get(initialFrameId());
    }
}
