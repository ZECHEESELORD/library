package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.HashSet;
import java.util.Set;

public final class MenuValidator {

    private MenuValidator() {
    }

    public static void validate(Menu menu) {
        if (menu.rows() < 1 || menu.rows() > 6) {
            throw new IllegalArgumentException("Menu rows must be between 1 and 6");
        }
        if (menu.frameIds().isEmpty()) {
            throw new IllegalArgumentException("Menu must contain at least one frame");
        }
        if (!menu.frameIds().contains(menu.initialFrameId())) {
            throw new IllegalArgumentException("Initial frame id must exist");
        }
        for (String frameId : menu.frameIds()) {
            validateFrame(menu.frame(frameId), menu.rows(), menu.title(), frameId);
        }
    }

    private static void validateFrame(MenuFrame frame, int rows, Object menuTitle, String frameId) {
        Set<Integer> seen = new HashSet<>();
        int maxSlot = rows * 9 - 1;
        for (MenuSlot slot : frame.slots()) {
            if (slot.slot() < 0 || slot.slot() > maxSlot) {
                throw new IllegalArgumentException("Slot " + slot.slot() + " is outside a " + rows + "-row menu");
            }
            if (!seen.add(slot.slot())) {
                throw new IllegalArgumentException("Duplicate slot " + slot.slot());
            }
            slot.interactions().forEach((click, interaction) -> {
                if (interaction.action() instanceof MenuSlotAction.Dispatch) {
                    throw new IllegalArgumentException("Compiled menu '" + menuTitle + "' frame '" + frameId
                            + "' uses dispatch interaction at slot " + slot.slot() + " for " + click);
                }
            });
        }
    }
}
