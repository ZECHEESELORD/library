package sh.harold.creative.library.menu;

import java.util.Map;

public interface Menu {

    MenuGeometry geometry();

    int rows();

    String initialFrameId();

    Map<String, MenuFrame> frames();

    default MenuFrame initialFrame() {
        return frames().get(initialFrameId());
    }
}
