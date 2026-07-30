package sh.harold.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuItemTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

class MenuItemTemplateTest {

    @Test
    void baseAndExactlyOneVariantComposeThenFreezeByInteractionPresence() {
        MenuItemTemplate<State> template = MenuItemTemplate
                .<State, Gate>builder(MenuIcon.vanilla("door"), State::gate)
                .base((state, item) -> item
                        .name("Portal")
                        .description("Shared destination details.")
                        .status(Component.text("Base status", NamedTextColor.GRAY)))
                .variant(Gate.OPEN, (state, item) -> item
                        .name("Enter Portal")
                        .line("Variant detail")
                        .status(Component.text("Ready!", NamedTextColor.GREEN))
                        .onLeftClick(ActionVerb.OPEN, context -> { }))
                .variant(Gate.LOCKED, (state, item) -> item
                        .status(Component.text("Complete all requirements to enter.", NamedTextColor.RED)))
                .build();

        MenuItem open = template.render(new State(Gate.OPEN));
        MenuItem locked = template.render(new State(Gate.LOCKED));

        assertInstanceOf(MenuButton.class, open);
        assertInstanceOf(MenuDisplayItem.class, locked);
        assertEquals("Enter Portal", ComponentText.flatten(open.name()));
        assertEquals(2, open.sections().size());
        assertEquals(1, open.statusLines().size());
        assertEquals("Ready!", ComponentText.flatten(open.statusLines().getFirst()));
        assertEquals("Complete all requirements to enter.",
                ComponentText.flatten(locked.statusLines().getFirst()));
    }

    @Test
    void fallbackHandlesUnregisteredClassifierValues() {
        MenuItemTemplate<String> template = MenuItemTemplate
                .<String, String>builder(MenuIcon.vanilla("paper"), value -> value)
                .base((state, item) -> item.name("Message"))
                .variant("known", (state, item) -> item.line("Known"))
                .otherwise((state, item) -> item.line("Fallback: " + state))
                .build();

        assertEquals(List.of("Fallback: other"),
                HouseMenuCompiler.compile(0, template.render("other")).lore().stream()
                        .map(ComponentText::flatten)
                        .toList());
    }

    @Test
    void duplicateVariantKeysFailWhileBuilding() {
        MenuItemTemplate.Builder<String, String> builder = MenuItemTemplate
                .<String, String>builder(MenuIcon.vanilla("paper"), value -> value)
                .base((state, item) -> item.name("Message"))
                .variant("same", (state, item) -> item.line("First"));

        builder.variant("same", (state, item) -> item.line("Second"));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void unmatchedStateWithoutFallbackFailsAtRenderTime() {
        MenuItemTemplate<String> template = MenuItemTemplate
                .<String, String>builder(MenuIcon.vanilla("paper"), value -> value)
                .base((state, item) -> item.name("Message"))
                .variant("known", (state, item) -> item.line("Known"))
                .build();

        assertThrows(IllegalStateException.class, () -> template.render("unknown"));
    }

    private enum Gate {
        OPEN,
        LOCKED
    }

    private record State(Gate gate) {
    }
}
