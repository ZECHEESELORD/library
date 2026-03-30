package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

public interface CanvasMenuBuilder {

    CanvasMenuBuilder title(String title);

    CanvasMenuBuilder title(Component title);

    CanvasMenuBuilder rows(int rows);

    CanvasMenuBuilder utility(UtilitySlot slot, MenuItem item);

    CanvasMenuBuilder place(int slot, MenuItem item);

    Menu build();
}
