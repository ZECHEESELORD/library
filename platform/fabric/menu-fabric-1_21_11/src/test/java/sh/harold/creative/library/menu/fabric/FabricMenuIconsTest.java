package sh.harold.creative.library.menu.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.MenuIcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuIconsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void customHeadRoundTripsWithoutThrowing() {
        String textureValue = "test-texture-value";

        ItemStack itemStack = FabricMenuIcons.createItem(MenuIcon.customHead(textureValue), 1);
        MenuIcon roundTripped = FabricMenuIcons.fromItemStack(itemStack);

        assertTrue(roundTripped.isCustomHead());
        assertEquals(textureValue, roundTripped.textureValue());
    }
}
