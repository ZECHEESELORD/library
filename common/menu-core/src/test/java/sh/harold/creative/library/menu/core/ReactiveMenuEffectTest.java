package sh.harold.creative.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ReactiveMenuEffect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactiveMenuEffectTest {

    @Test
    void viewerInventorySlotEffectRejectsNegativeSlots() {
        assertThrows(IllegalArgumentException.class, () -> new ReactiveMenuEffect.SetViewerInventorySlot(-1, null));
    }

    @Test
    void viewerInventorySlotEffectAllowsClearOperations() {
        ReactiveMenuEffect.SetViewerInventorySlot effect = new ReactiveMenuEffect.SetViewerInventorySlot(7, null);

        assertEquals(7, effect.slot());
        assertNull(effect.stack());
    }
}
