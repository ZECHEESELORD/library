package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
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
                Component.text("Museum Rewards", TextColor.color(0xFFAA00)),
                List.of(
                        Component.text()
                                .append(Component.text("Bits Available: ", NamedTextColor.GRAY))
                                .append(Component.text("10,420", TextColor.color(0x55FFFF)))
                                .build(),
                        Component.empty(),
                        Component.text("CLICK to view")),
                true,
                Map.of());

        ItemStack rendered = new MinestomMenuRenderer().render(slot);

        assertEquals("minecraft:emerald", rendered.material().key().asString());
        assertEquals("Museum Rewards", flatten(rendered.get(DataComponents.CUSTOM_NAME)));
        assertEquals(TextColor.color(0xFFAA00), rendered.get(DataComponents.CUSTOM_NAME).color());
        assertEquals(List.of("Bits Available: 10,420", "", "CLICK to view"),
                rendered.get(DataComponents.LORE).stream().map(MinestomMenuRendererTest::flatten).toList());
        assertEquals(NamedTextColor.GRAY, rendered.get(DataComponents.LORE).getFirst().children().get(0).color());
        assertEquals(TextColor.color(0x55FFFF), rendered.get(DataComponents.LORE).getFirst().children().get(1).color());
        assertTrue(Boolean.TRUE.equals(rendered.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
        assertEquals(ItemStack.of(Material.EMERALD).withoutExtraTooltip().get(DataComponents.TOOLTIP_DISPLAY),
                rendered.get(DataComponents.TOOLTIP_DISPLAY));
        assertTrue(rendered.get(DataComponents.TOOLTIP_DISPLAY).hiddenComponents().contains(DataComponents.ATTRIBUTE_MODIFIERS));
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
