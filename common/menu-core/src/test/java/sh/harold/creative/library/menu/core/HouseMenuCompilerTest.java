package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuOptionLine;
import sh.harold.creative.library.menu.MenuPair;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.ui.value.UiValues;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    void mutedLineRendersAsSeparateDarkGrayMetadataBlock() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("lightning_rod"))
                .name("Lightning")
                .secondary("Starter Pack")
                .description("Call lightning on the active player. Damage is optional.")
                .mutedLine("ID: TWITCH.LIGHTNING")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "Starter Pack",
                "",
                "Call lightning on the active",
                "player. Damage is optional.",
                "",
                "ID: TWITCH.LIGHTNING"), lore(slot));
        assertEquals(NamedTextColor.DARK_GRAY, slot.lore().getLast().color());
    }

    @Test
    void optionLinesRenderSelectedArrowAndMuteInactiveColors() {
        TextColor selectedColor = TextColor.color(0xFFAA00);
        TextColor inactiveColor = TextColor.color(0x55FFFF);
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                .name("Filter")
                .optionLines(
                        new MenuOptionLine("All", selectedColor, true),
                        new MenuOptionLine("Tag: pain", inactiveColor, false))
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of("→ All", "   Tag: pain"), lore(slot));
        Component selectedLine = slot.lore().getFirst();
        Component inactiveLine = slot.lore().get(1);

        assertEquals(TextDecoration.State.TRUE, selectedLine.children().get(0).decoration(TextDecoration.BOLD));
        assertEquals(selectedColor, selectedLine.children().get(0).color());
        assertEquals(selectedColor, selectedLine.children().get(1).color());
        assertNotEquals(TextDecoration.State.TRUE, inactiveLine.children().get(0).decoration(TextDecoration.BOLD));
        assertEquals(inactiveLine.children().get(0).color(), inactiveLine.children().get(1).color());
        assertNotEquals(inactiveColor, inactiveLine.children().get(1).color());
        assertNotEquals(selectedColor, inactiveLine.children().get(1).color());
    }

    @Test
    void optionLinesCanRenderSlidingWindowAroundSelection() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("comparator"))
                .name("Sort")
                .optionLines(3, List.of(
                        new MenuOptionLine("A-Z", TextColor.color(0xF7E29A), false),
                        new MenuOptionLine("Z-A", TextColor.color(0xE8BE74), false),
                        new MenuOptionLine("Popularity", TextColor.color(0xFF8C57), true),
                        new MenuOptionLine("Category", TextColor.color(0x73C9B7), false),
                        new MenuOptionLine("Tags", TextColor.color(0x7DB4F5), false)))
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of("   Z-A", "→ Popularity", "   Category"), lore(slot));
    }

    @Test
    void bulletsStayOnOneLineUntilTheyReachTheBulletHardLimit() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("golden_hoe"))
                .name("Farming XLIX")
                .bullet("Grants +196 to +200 Farming Fortune")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of("• Grants +196 to +200 Farming Fortune"), lore(slot));
    }

    @Test
    void bulletsKeepHangingIndentOnceTheyExceedTheBulletHardLimit() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("golden_hoe"))
                .name("Farming XLIX")
                .bullet("Review every centered layout guideline before placing showcase cards across the wider canvas.")
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of(
                "• Review every centered layout guideline before",
                "  placing showcase cards across the wider canvas."), lore(slot));
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
    void valueLinesPreserveExplicitArgumentColors() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .valueLine("Bits Available ", UiValues.prettyNumber(10_420, 0x55FFFF))
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        Component line = slot.lore().getFirst();
        assertEquals(NamedTextColor.GRAY, line.children().get(0).color());
        assertEquals(TextColor.color(0x55FFFF), line.children().get(1).color());
    }

    @Test
    void itemTitlesPreserveExplicitFeelColors() {
        MenuSlot reward = HouseMenuCompiler.compile(13, MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name(FakeSkyBlockMenuTitles.reward("Museum Rewards"))
                .build());
        MenuSlot perk = HouseMenuCompiler.compile(13, MenuDisplayItem.builder(MenuIcon.vanilla("beacon"))
                .name(FakeSkyBlockMenuTitles.perk("Perks"))
                .build());

        assertEquals(TextColor.color(0xFFAA00), reward.title().color());
        assertEquals(TextColor.color(0x55FFFF), perk.title().color());
    }

    @Test
    void pairValuesPreserveExplicitColors() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .pair("Cost", UiValues.prettyNumber(2_750, 0x55FF55))
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        Component line = slot.lore().getFirst();
        assertEquals(NamedTextColor.GRAY, line.children().get(0).color());
        assertEquals(TextColor.color(0x55FF55), line.children().get(1).color());
    }

    @Test
    void menuPairFactoryPreservesUiValueWhenValueIsTypedAsObject() {
        Object xpToNext = UiValues.prettyNumber(4_800, 0x55FF55);
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .pairs(MenuPair.of("XP to Next", xpToNext))
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(List.of("XP to Next: 4,800"), lore(slot));
        Component line = slot.lore().getFirst();
        assertEquals(NamedTextColor.GRAY, line.children().get(0).color());
        assertEquals(TextColor.color(0x55FF55), line.children().get(1).color());
    }

    @Test
    void softValueLinesKeepArgumentColorOnContinuation() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .softValueLine("Selected Route: ", UiValues.literal(
                        "This deliberately long value should wrap and keep its shared accent color.", 0x55FFFF))
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, item);

        assertEquals(TextColor.color(0x55FFFF), slot.lore().get(1).color());
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
    void exactLoreIsPreservedBeforePromptWithoutCompilingBodyBlocks() {
        MenuButton button = MenuButton.builder(MenuIcon.vanilla("book"))
                .exactName(Component.text("Exact Button"))
                .exactLore(Component.text("First Exact Line"), Component.text("Second Exact Line"))
                .line("Ignored body")
                .action(ActionVerb.SELECT, "give the item", context -> {})
                .build();

        MenuSlot slot = HouseMenuCompiler.compile(13, button);

        assertEquals(List.of(
                "First Exact Line",
                "Second Exact Line",
                "",
                "CLICK to give the item!"), lore(slot));
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
