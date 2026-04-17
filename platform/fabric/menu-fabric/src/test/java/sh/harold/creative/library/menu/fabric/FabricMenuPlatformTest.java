package sh.harold.creative.library.menu.fabric;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.MenuTooltipBehavior;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuPlatformTest {

    @Test
    void buttonFromItemStackDefaultsToChromeAndPreservesStyledPresentation() {
        try (FabricMenuPlatform menus = new FabricMenuPlatform()) {
            ItemStack source = styledStack();

            MenuButton button = menus.button(source)
                    .action(ActionVerb.OPEN, context -> { })
                    .build();

            assertEquals(MenuTooltipBehavior.CHROME, button.tooltipBehavior());
            assertEquals(7, button.amount());
            assertTrue(button.glow());
            assertStyledText(button.name(), "Styled Name", 0xFFAA00, true);
            assertEquals(2, button.exactLore().orElseThrow().size());
            assertStyledText(button.exactLore().orElseThrow().get(0), "First lore line", 0x55FFFF, false);
            assertStyledText(button.exactLore().orElseThrow().get(1), "Second lore line", 0xAAAAAA, false);
        }
    }

    @Test
    void displayFromItemStackDefaultsToChrome() {
        try (FabricMenuPlatform menus = new FabricMenuPlatform()) {
            MenuDisplayItem display = menus.display(styledStack()).build();

            assertEquals(MenuTooltipBehavior.CHROME, display.tooltipBehavior());
            assertEquals(7, display.amount());
            assertTrue(display.glow());
        }
    }

    @Test
    void stackFromItemStackRemainsLiteral() {
        try (FabricMenuPlatform menus = new FabricMenuPlatform()) {
            MenuStack stack = menus.stack(styledStack()).build();

            assertEquals(MenuTooltipBehavior.LITERAL, stack.tooltipBehavior());
            assertEquals(7, stack.amount());
            assertTrue(stack.glow());
            assertStyledText(stack.name(), "Styled Name", 0xFFAA00, true);
        }
    }

    private static ItemStack styledStack() {
        ItemStack stack = new ItemStack(Items.NETHER_STAR, 7);
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Styled Name")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                net.minecraft.network.chat.Component.literal("First lore line")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(false)),
                net.minecraft.network.chat.Component.literal("Second lore line")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))
        )));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        return stack;
    }

    private static void assertStyledText(Component component, String content, int rgb, boolean bold) {
        TextComponent text = (TextComponent) component;
        assertEquals(content, text.content());
        assertEquals(TextColor.color(rgb), text.color());
        assertEquals(bold ? TextDecoration.State.TRUE : TextDecoration.State.FALSE, text.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.FALSE, text.decoration(TextDecoration.ITALIC));
    }
}
