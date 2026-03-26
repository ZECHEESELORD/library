package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseMenuCompilerTest {

    @Test
    void singularLinesRenderAsSeparatedBlocks() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .line("a")
                .line("b")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of("a", "", "b"), lore(slot));
    }

    @Test
    void pluralLinesRenderAsContiguousBlock() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .lines("a", "b")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of("a", "b"), lore(slot));
    }

    @Test
    void promptRendersLastAfterBodyBlocks() {
        MenuButton button = MenuButton.builder(MenuIcon.vanilla("book"))
                .name("Button")
                .line("Body")
                .action(ActionVerb.VIEW, context -> {})
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, button);

        assertEquals(List.of("Body", "", "CLICK to view"), lore(slot));
    }

    @Test
    void progressUsesHouseFormattingAndAccentSegmentation() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("experience_bottle"))
                .name("Progress")
                .progress("Progress to Level 2", 200, 1_000, AccentFamily.GOLD)
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "Progress to Level 2: 20%",
                "-------------------- 200/1,000"), lore(slot));

        Component firstLine = slot.lore().getFirst();
        Component secondLine = slot.lore().get(1);

        assertEquals(2, firstLine.children().size());
        assertEquals(NamedTextColor.GRAY, firstLine.children().get(0).color());
        assertEquals(AccentFamily.GOLD.light(), firstLine.children().get(1).color());

        assertEquals(6, secondLine.children().size());
        assertEquals(AccentFamily.GOLD.light(), secondLine.children().get(0).color());
        assertEquals(NamedTextColor.GRAY, secondLine.children().get(1).color());
        assertEquals(AccentFamily.GOLD.dark(), secondLine.children().get(4).color());
        assertEquals(AccentFamily.GOLD.light(), secondLine.children().get(5).color());
    }

    @Test
    void progressPercentKeepsSingleDecimalWhenNeeded() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("experience_bottle"))
                .name("Progress")
                .progress("Progress to Level L", 3_432_908, 4_000_000, AccentFamily.GOLD)
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals("Progress to Level L: 85.8%", ComponentText.flatten(slot.lore().getFirst()));
    }

    private static List<String> lore(MenuSlot slot) {
        return slot.lore().stream()
                .map(ComponentText::flatten)
                .toList();
    }
}
