package sh.harold.creative.library.menu.fabric;

import net.minecraft.world.inventory.ClickType;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.MenuClick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuContainerTest {

    @Test
    void resolveClickAcceptsLiteralAndShiftVariantsOnly() {
        FabricMenuContainer.ClickBinding left = FabricMenuContainer.resolveClick(ClickType.PICKUP, 0);
        FabricMenuContainer.ClickBinding right = FabricMenuContainer.resolveClick(ClickType.PICKUP, 1);
        FabricMenuContainer.ClickBinding shiftedLeft = FabricMenuContainer.resolveClick(ClickType.QUICK_MOVE, 0);
        FabricMenuContainer.ClickBinding shiftedRight = FabricMenuContainer.resolveClick(ClickType.QUICK_MOVE, 1);

        assertEquals(MenuClick.LEFT, left.button());
        assertFalse(left.shift());
        assertEquals(MenuClick.RIGHT, right.button());
        assertFalse(right.shift());
        assertEquals(MenuClick.LEFT, shiftedLeft.button());
        assertTrue(shiftedLeft.shift());
        assertEquals(MenuClick.RIGHT, shiftedRight.button());
        assertTrue(shiftedRight.shift());
    }

    @Test
    void resolveClickRejectsSpamProneUnsupportedVariants() {
        assertNull(FabricMenuContainer.resolveClick(ClickType.PICKUP_ALL, 0));
        assertNull(FabricMenuContainer.resolveClick(ClickType.SWAP, 0));
        assertNull(FabricMenuContainer.resolveClick(ClickType.CLONE, 0));
        assertNull(FabricMenuContainer.resolveClick(ClickType.THROW, 0));
        assertNull(FabricMenuContainer.resolveClick(ClickType.PICKUP, 2));
    }
}
