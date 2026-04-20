package sh.harold.creative.library.menu.fabric;

import net.minecraft.world.inventory.ContainerInput;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.MenuClick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuContainerTest {

    @Test
    void resolveClickAcceptsLiteralAndShiftVariantsOnly() {
        FabricMenuContainer.ClickBinding left = FabricMenuContainer.resolveClick(ContainerInput.PICKUP, 0);
        FabricMenuContainer.ClickBinding right = FabricMenuContainer.resolveClick(ContainerInput.PICKUP, 1);
        FabricMenuContainer.ClickBinding shiftedLeft = FabricMenuContainer.resolveClick(ContainerInput.QUICK_MOVE, 0);
        FabricMenuContainer.ClickBinding shiftedRight = FabricMenuContainer.resolveClick(ContainerInput.QUICK_MOVE, 1);

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
        assertNull(FabricMenuContainer.resolveClick(ContainerInput.PICKUP_ALL, 0));
        assertNull(FabricMenuContainer.resolveClick(ContainerInput.SWAP, 0));
        assertNull(FabricMenuContainer.resolveClick(ContainerInput.CLONE, 0));
        assertNull(FabricMenuContainer.resolveClick(ContainerInput.THROW, 0));
        assertNull(FabricMenuContainer.resolveClick(ContainerInput.PICKUP, 2));
    }
}
