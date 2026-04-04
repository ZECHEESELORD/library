package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperMenuRendererTest {

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

        AtomicReference<MenuIcon> createdIcon = new AtomicReference<>();
        ItemStack rendered = new PaperMenuRenderer(icon -> {
            createdIcon.set(icon);
            return PaperMenuTestSupport.renderedItem(icon.key(), 1, null, null, false);
        }).render(slot);
        org.bukkit.inventory.meta.ItemMeta renderedMeta = rendered.getItemMeta();

        assertEquals(MenuIcon.vanilla("emerald"), createdIcon.get());
        assertEquals("Museum Rewards", flatten(renderedMeta.displayName()));
        assertEquals(TextColor.color(0xFFAA00), renderedMeta.displayName().color());
        assertEquals(List.of("Bits Available: 10,420", "", "CLICK to view"),
                renderedMeta.lore().stream().map(PaperMenuRendererTest::flatten).toList());
        assertEquals(NamedTextColor.GRAY, renderedMeta.lore().getFirst().children().get(0).color());
        assertEquals(TextColor.color(0x55FFFF), renderedMeta.lore().getFirst().children().get(1).color());
        assertTrue(Boolean.TRUE.equals(renderedMeta.getEnchantmentGlintOverride()));
        assertEquals(Set.of(ItemFlag.values()), renderedMeta.getItemFlags());
    }

    @Test
    void rendererPassesCustomHeadIconsToFactory() {
        MenuSlot slot = new MenuSlot(
                13,
                MenuIcon.customHead("dGV4dHVyZS12YWx1ZQ=="),
                Component.text("Custom Head"),
                List.of(),
                false,
                Map.of());

        AtomicReference<MenuIcon> createdIcon = new AtomicReference<>();
        new PaperMenuRenderer(icon -> {
            createdIcon.set(icon);
            return PaperMenuTestSupport.renderedItem(icon.key(), 1, null, null, false);
        }).render(slot);

        assertEquals(MenuIcon.customHead("dGV4dHVyZS12YWx1ZQ=="), createdIcon.get());
        assertTrue(createdIcon.get().isCustomHead());
        assertEquals("dGV4dHVyZS12YWx1ZQ==", createdIcon.get().textureValue());
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
