package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    void descriptionWrapsByCharacterCountAndIgnoresTitleLength() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("An Extremely Long Menu Title That Should Not Affect Lore Wrapping")
                .description("View your equipment, stats, and more!")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "View your equipment,",
                "stats, and more!"), lore(slot));
    }

    @Test
    void softLineStaysSingleLineWhenItFitsWithinThirtyCharacters() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .softLine("Alpha Bravo Charlie Delta Echo")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of("Alpha Bravo Charlie Delta Echo"), lore(slot));
    }

    @Test
    void descriptionBalancesLinesToAvoidHangingLastWord() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("golden_hoe"))
                .name("Farming XLIX")
                .description("Harvest crops and shear sheep to earn Farming XP!")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "Harvest crops and shear",
                "sheep to earn Farming XP!"), lore(slot));
    }

    @Test
    void bulletsKeepHangingIndentWhileBalancingLines() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("golden_hoe"))
                .name("Farming XLIX")
                .bullet("Grants +196 to +200 Farming Fortune")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "• Grants +196 to +200",
                "  Farming Fortune"), lore(slot));
    }

    @Test
    void multiEntrySoftLinesStayOnSingleLoreLines() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .softLines(
                        "This deliberately long stat line should stay intact.",
                        "This second deliberately long stat line should also stay intact.")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "This deliberately long stat line should stay intact.",
                "This second deliberately long stat line should also stay intact."), lore(slot));
    }

    @Test
    void multiEntrySoftPairsStayOnSingleLoreLines() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .softPairs(
                        "Selected Power", "Silky reforges grant a lot of crit damage for this setup.",
                        "Stored Layout", "This wardrobe loadout should stay on one pair line.")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "Selected Power: Silky reforges grant a lot of crit damage for this setup.",
                "Stored Layout: This wardrobe loadout should stay on one pair line."), lore(slot));
    }

    @Test
    void promptRendersLastAfterBodyBlocks() {
        MenuButton button = MenuButton.builder(MenuIcon.vanilla("book"))
                .name("Button")
                .line("Body")
                .action(ActionVerb.VIEW, context -> {})
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, button);

        assertEquals(List.of("Body", "", "CLICK to view!"), lore(slot));
    }

    @Test
    void dualPromptsUseClickAndRightClickWithoutExtraSpacing() {
        MenuButton button = MenuButton.builder(MenuIcon.vanilla("book"))
                .name("Button")
                .line("Body")
                .action(ActionVerb.OPEN, context -> {})
                .onRightClick(ActionVerb.BROWSE, context -> {})
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, button);

        assertEquals(List.of("Body", "", "CLICK to open!", "RIGHT CLICK to browse!"), lore(slot));

        Component leftPrompt = slot.lore().get(2);
        Component rightPrompt = slot.lore().get(3);

        assertEquals(NamedTextColor.YELLOW, leftPrompt.children().get(0).color());
        assertEquals(TextDecoration.State.TRUE, leftPrompt.children().get(0).decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.YELLOW, leftPrompt.children().get(1).color());

        assertEquals(NamedTextColor.AQUA, rightPrompt.children().get(0).color());
        assertEquals(TextDecoration.State.TRUE, rightPrompt.children().get(0).decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.AQUA, rightPrompt.children().get(1).color());
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
        assertEquals(TextDecoration.State.TRUE, secondLine.children().get(0).decoration(TextDecoration.STRIKETHROUGH));
        assertEquals(NamedTextColor.GRAY, secondLine.children().get(1).color());
        assertEquals(TextDecoration.State.TRUE, secondLine.children().get(1).decoration(TextDecoration.STRIKETHROUGH));
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
