package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinestomMenuRendererTest {

    @Test
    void rendererMatchesSharedParitySnapshot() {
        MenuSlot slot = new MenuSlot(
                13,
                MenuIcon.vanilla("emerald"),
                Component.text("Museum Rewards"),
                List.of(Component.text("Every 100 SkyBlock XP"), Component.empty(), Component.text("CLICK to view")),
                true,
                Map.of());

        ItemStack rendered = new MinestomMenuRenderer().render(slot);

        assertEquals("minecraft:emerald", rendered.material().key().asString());
        assertEquals("Museum Rewards", flatten(rendered.get(DataComponents.CUSTOM_NAME)));
        assertEquals(List.of("Every 100 SkyBlock XP", "", "CLICK to view"),
                rendered.get(DataComponents.LORE).stream().map(MinestomMenuRendererTest::flatten).toList());
        assertTrue(Boolean.TRUE.equals(rendered.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
    }

    private static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            append(builder, child);
        }
    }
}
