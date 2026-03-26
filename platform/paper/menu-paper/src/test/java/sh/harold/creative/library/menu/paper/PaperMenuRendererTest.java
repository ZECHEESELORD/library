package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperMenuRendererTest {

    @Test
    void rendererMatchesSharedParitySnapshot() {
        MenuSlot slot = new MenuSlot(
                13,
                MenuIcon.vanilla("emerald"),
                Component.text("Museum Rewards"),
                List.of(Component.text("Every 100 SkyBlock XP"), Component.empty(), Component.text("CLICK to view")),
                true,
                Map.of());

        AtomicReference<String> createdKey = new AtomicReference<>();
        ItemMeta meta = mock(ItemMeta.class);
        ItemStack itemStack = mock(ItemStack.class);
        AtomicReference<Component> displayName = new AtomicReference<>();
        AtomicReference<List<Component>> lore = new AtomicReference<>();
        AtomicBoolean glint = new AtomicBoolean();
        when(itemStack.getItemMeta()).thenReturn(meta);
        when(meta.displayName()).thenAnswer(invocation -> displayName.get());
        when(meta.lore()).thenAnswer(invocation -> lore.get());
        when(meta.getEnchantmentGlintOverride()).thenAnswer(invocation -> glint.get() ? Boolean.TRUE : null);
        doAnswer(invocation -> {
            displayName.set(invocation.getArgument(0));
            return null;
        }).when(meta).displayName(ArgumentMatchers.any());
        doAnswer(invocation -> {
            lore.set(invocation.getArgument(0));
            return null;
        }).when(meta).lore(ArgumentMatchers.any());
        doAnswer(invocation -> {
            glint.set(Boolean.TRUE.equals(invocation.getArgument(0)));
            return null;
        }).when(meta).setEnchantmentGlintOverride(ArgumentMatchers.any());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ItemMeta> editor = invocation.getArgument(0);
            editor.accept(meta);
            return true;
        }).when(itemStack).editMeta(ArgumentMatchers.any());

        ItemStack rendered = new PaperMenuRenderer(key -> {
            createdKey.set(key);
            return itemStack;
        }).render(slot);
        ItemMeta renderedMeta = rendered.getItemMeta();

        assertEquals("minecraft:emerald", createdKey.get());
        assertEquals("Museum Rewards", flatten(renderedMeta.displayName()));
        assertEquals(List.of("Every 100 SkyBlock XP", "", "CLICK to view"),
                renderedMeta.lore().stream().map(PaperMenuRendererTest::flatten).toList());
        assertTrue(Boolean.TRUE.equals(renderedMeta.getEnchantmentGlintOverride()));
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
