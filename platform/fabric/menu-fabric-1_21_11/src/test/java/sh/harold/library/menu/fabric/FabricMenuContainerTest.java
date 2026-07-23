package sh.harold.library.menu.fabric;

import net.kyori.adventure.text.Component;
import net.minecraft.world.inventory.ClickType;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.MenuClick;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuContainerTest {

    @Test
    void dialogPromptCannotEscapeWithoutCompleting() {
        assertFalse(FabricMenuRuntime.PROMPT_CAN_CLOSE_WITH_ESCAPE);
    }

    @Test
    void custodySettlementOnlyScansOrdinaryPlayerStorage() {
        assertEquals(36, FabricMenuRuntime.STORAGE_SLOT_COUNT);
    }

    @Test
    void titleOrRowChangeRequiresContainerRebuild() {
        assertFalse(FabricMenuSession.containerMetadataChanged(
                3, Component.text("Stable"), 3, Component.text("Stable")));
        assertTrue(FabricMenuSession.containerMetadataChanged(
                3, Component.text("Before"), 3, Component.text("After")));
        assertTrue(FabricMenuSession.containerMetadataChanged(
                3, Component.text("Stable"), 4, Component.text("Stable")));
    }

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

    @Test
    void unchangedDispatchSkipsTheEntireRenderSyncAndBroadcastPath() {
        AtomicInteger renders = new AtomicInteger();

        assertFalse(FabricMenuRuntime.renderIfStateChanged(12L, 12L, renders::incrementAndGet));
        assertEquals(0, renders.get());
        assertTrue(FabricMenuRuntime.renderIfStateChanged(12L, 13L, renders::incrementAndGet));
        assertEquals(1, renders.get());
    }
}
