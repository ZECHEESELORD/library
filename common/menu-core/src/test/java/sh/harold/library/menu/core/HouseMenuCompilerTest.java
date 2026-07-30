package sh.harold.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuChecklistEntry;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuProgress;
import sh.harold.library.menu.MenuProgressPalette;
import sh.harold.library.menu.MenuSlot;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseMenuCompilerTest {

    @Test
    void sectionsHaveExactlyOneBlankBetweenThemAndNoneAtTheEdges() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Oddities")
                .section(section -> section
                        .valueLine(Component.text("Buy price: ", NamedTextColor.GRAY),
                                Component.text("12,683,091 coins", NamedTextColor.GOLD))
                        .mutedLine("5.9k in 409 offers")
                        .mutedLine("90.5k insta-buys in 7d"))
                .description("A second semantic section.")
                .build();

        assertEquals(List.of(
                "Buy price: 12,683,091 coins",
                "5.9k in 409 offers",
                "90.5k insta-buys in 7d",
                "",
                "A second semantic section."), lore(item));
    }

    @Test
    void directCallsEachCreateASectionWhileMixedSectionRowsRemainContiguous() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                .name("Card")
                .line("a")
                .line("b")
                .section(section -> section.line("c").mutedLine("d"))
                .build();

        assertEquals(List.of("a", "", "b", "", "c", "d"), lore(item));
    }

    @Test
    void greedyWrappingUsesAnExact240PixelBoundary() {
        String exact = "a".repeat(34) + "!";
        assertEquals(240, MinecraftFontMetrics.width(exact));

        MenuDisplayItem exactItem = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("Boundary")
                .description(exact)
                .build();
        MenuDisplayItem overflow = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("Boundary")
                .description(exact + " b")
                .build();

        assertEquals(List.of(exact), lore(exactItem));
        assertEquals(List.of(exact, "b"), lore(overflow));
    }

    @Test
    void wrappingCollapsesWhitespaceAndIgnoresItemTitleWidth() {
        String paragraph = "  Alpha\n  beta\t gamma   delta  ";
        MenuDisplayItem shortTitle = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("A")
                .description(paragraph)
                .build();
        MenuDisplayItem longTitle = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("An Extremely Long Title Which Must Never Affect Lore Width")
                .description(paragraph)
                .build();

        assertEquals(List.of("Alpha beta gamma delta"), lore(shortTitle));
        assertEquals(lore(shortTitle), lore(longTitle));
    }

    @Test
    void resolvedBoldStyleChangesWrappingAndCannotCollideInTheCache() {
        String prose = "a ".repeat(20).trim();
        Component bold = Component.text(prose).decorate(TextDecoration.BOLD);

        MenuDisplayItem plainItem = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("Plain")
                .description(prose)
                .build();
        MenuDisplayItem boldItem = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("Bold")
                .description(bold)
                .build();

        assertEquals(1, HouseMenuCompiler.compile(0, plainItem).lore().size());
        assertEquals(2, HouseMenuCompiler.compile(0, boldItem).lore().size());
        assertTrue(HouseMenuCompiler.compile(0, boldItem).lore().stream()
                .flatMap(line -> runs(line).stream())
                .allMatch(run -> run.text().isBlank()
                        || run.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE));
    }

    @Test
    void styledSpansSurviveLineBreaks() {
        Component prose = Component.text()
                .append(Component.text("Alpha beta gamma delta epsilon ", NamedTextColor.GRAY))
                .append(Component.text("zeta eta theta iota kappa lambda mu", NamedTextColor.AQUA))
                .build();
        MenuSlot slot = HouseMenuCompiler.compile(0, MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("Styled")
                .description(prose)
                .build());

        assertTrue(slot.lore().size() > 1);
        assertTrue(slot.lore().stream()
                .flatMap(line -> runs(line).stream())
                .anyMatch(run -> run.style().color() == NamedTextColor.AQUA));
        assertTrue(slot.lore().getLast().children().stream()
                .flatMap(child -> runs(child).stream())
                .anyMatch(run -> run.style().color() == NamedTextColor.AQUA));
    }

    @Test
    void structuredPrefixesConsumeWidthAndContinuationUsesNearestSpaceIndent() {
        String value = "a ".repeat(20).trim();
        Component prefix = Component.text("Selected Route: ", NamedTextColor.GRAY);
        MenuSlot slot = HouseMenuCompiler.compile(0, MenuDisplayItem.builder(MenuIcon.vanilla("map"))
                .name("Route")
                .valueLine(prefix, Component.text(value, NamedTextColor.AQUA))
                .build());

        assertTrue(slot.lore().size() > 1);
        String continuation = ComponentText.flatten(slot.lore().get(1));
        int leadingSpaces = continuation.length() - continuation.stripLeading().length();
        assertTrue(leadingSpaces > 0);
        assertTrue(Math.abs(
                MinecraftFontMetrics.width(" ".repeat(leadingSpaces))
                        - MinecraftFontMetrics.width(prefix)) <= 3);
        assertTrue(slot.lore().stream().allMatch(line -> MinecraftFontMetrics.width(line) <= 240));
    }

    @Test
    void unicodeUsesCodePointsAndOrdinaryLongWordsAreNeverSplit() {
        assertEquals(6, MinecraftFontMetrics.width("😀"));
        String longWord = "😀".repeat(50);
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("Unicode")
                .description(longWord)
                .build();

        assertEquals(List.of(longWord), lore(item));
        assertTrue(MinecraftFontMetrics.width(HouseMenuCompiler.compile(0, item).lore().getFirst()) > 240);
    }

    @Test
    void explicitAndMutedLinesRemainLiteral() {
        String longLine = "This deliberately long literal line remains intact even when its pixel width is well beyond the prose budget.";
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("paper"))
                .name("Literal")
                .line(longLine)
                .mutedLine(longLine)
                .build();

        assertEquals(List.of(longLine, "", longLine), lore(item));
        assertEquals(NamedTextColor.GRAY, resolvedColor(HouseMenuCompiler.compile(0, item).lore().getFirst()));
        assertEquals(NamedTextColor.DARK_GRAY, resolvedColor(HouseMenuCompiler.compile(0, item).lore().getLast()));
    }

    @Test
    void checklistUsesInsetMarkersAndPreservesIndependentLabelStyles() {
        MenuDisplayItem item = MenuDisplayItem.builder(MenuIcon.vanilla("writable_book"))
                .name("Requirements")
                .checklist(
                        MenuChecklistEntry.complete(Component.text("Reach level 10", NamedTextColor.YELLOW)),
                        MenuChecklistEntry.incomplete(Component.text("Find the key", NamedTextColor.AQUA)))
                .build();
        MenuSlot slot = HouseMenuCompiler.compile(0, item);

        assertEquals(List.of(" ✔ Reach level 10", " ✖ Find the key"), lore(item));
        assertTrue(hasRun(slot.lore().get(0), "✔", NamedTextColor.GREEN));
        assertTrue(hasRun(slot.lore().get(0), "Reach", NamedTextColor.YELLOW));
        assertTrue(hasRun(slot.lore().get(1), "✖", NamedTextColor.RED));
        assertTrue(hasRun(slot.lore().get(1), "Find", NamedTextColor.AQUA));
    }

    @Test
    void terminalStatusRendersAfterSectionsAndBeforePrompts() {
        MenuButton item = MenuButton.builder(MenuIcon.vanilla("barrier"))
                .name("Locked")
                .description("Travel to the sealed chamber.")
                .status(Component.text("Complete all requirements to enter.", NamedTextColor.RED))
                .onLeftClick(ActionVerb.VIEW, context -> { })
                .build();

        assertEquals(List.of(
                "Travel to the sealed chamber.",
                "",
                "Complete all requirements to enter.",
                "",
                "CLICK to view!"), lore(item));
    }

    @Test
    void everyPromptIsYellowOrderedAndOnlyItsGestureIsBold() {
        MenuButton item = MenuButton.builder(MenuIcon.vanilla("book"))
                .name("Actions")
                .onLeftClick(ActionVerb.OPEN, context -> { })
                .onShiftLeftClick(ActionVerb.CONFIRM, "archive", context -> { })
                .onRightClick(ActionVerb.BROWSE, context -> { })
                .onShiftRightClick(ActionVerb.SELECT, "preview", context -> { })
                .build();
        MenuSlot slot = HouseMenuCompiler.compile(0, item);

        assertEquals(List.of(
                "CLICK to open!",
                "SHIFT CLICK to archive!",
                "RIGHT CLICK to browse!",
                "SHIFT RIGHT CLICK to preview!"), lore(item));
        for (Component prompt : slot.lore()) {
            List<ResolvedRun> promptRuns = runs(prompt);
            assertEquals(NamedTextColor.YELLOW, promptRuns.get(0).style().color());
            assertEquals(TextDecoration.State.TRUE,
                    promptRuns.get(0).style().decoration(TextDecoration.BOLD));
            assertEquals(NamedTextColor.YELLOW, promptRuns.get(1).style().color());
            assertEquals(TextDecoration.State.FALSE,
                    promptRuns.get(1).style().decoration(TextDecoration.BOLD));
            assertTrue(promptRuns.get(1).text().endsWith("!"));
        }
    }

    @Test
    void progressUsesCeilingFillUnclampedPercentAndTwentyFiveSpaceBar() {
        assertProgress(-10, 100, 0, "-10%");
        assertProgress(0, 100, 0, "0%");
        assertProgress(0.01, 100, 1, "0%");
        assertProgress(25, 100, 7, "25%");
        assertProgress(100, 100, 25, "100%");
        assertProgress(125, 100, 25, "125%");
    }

    @Test
    void progressFormatsDecimalsUnitsAndAquaPalette() {
        MenuProgress progress = MenuProgress.builder("Charge", 12.5, 20.5)
                .unit("MP")
                .palette(MenuProgressPalette.AQUA)
                .build();
        MenuSlot slot = HouseMenuCompiler.compile(0, MenuDisplayItem.builder(MenuIcon.vanilla("experience_bottle"))
                .name("Charge")
                .progress(progress)
                .build());

        assertEquals("Charge: 61%", ComponentText.flatten(slot.lore().getFirst()));
        assertTrue(hasRun(slot.lore().getFirst(), "61%", NamedTextColor.AQUA));
        assertTrue(ComponentText.flatten(slot.lore().get(1)).endsWith("12.5/20.5 MP"));
        assertTrue(runs(slot.lore().get(1)).stream()
                .filter(run -> run.style().decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE)
                .allMatch(run -> run.style().color() == NamedTextColor.DARK_AQUA
                        || run.style().color() == NamedTextColor.WHITE));
    }

    @Test
    void exactLoreRemainsLiteralButSemanticSectionsAndPromptFollowNormally() {
        MenuButton item = MenuButton.builder(MenuIcon.vanilla("book"))
                .exactName(Component.text("Native"))
                .exactLore(Component.text("Native line"))
                .line("Semantic line")
                .onLeftClick(ActionVerb.SELECT, context -> { })
                .literalItem()
                .build();

        assertEquals(List.of("Native line", "", "Semantic line", "", "CLICK to select!"), lore(item));
        assertEquals(1, HouseMenuCompiler.compile(0, item).replaceableLoreLineCount());
    }

    private static void assertProgress(Number current, Number max, int expectedFill, String expectedPercent) {
        MenuSlot slot = HouseMenuCompiler.compile(0, MenuDisplayItem.builder(MenuIcon.vanilla("experience_bottle"))
                .name("Progress")
                .progress("Progress", current, max)
                .build());
        assertEquals("Progress: " + expectedPercent, ComponentText.flatten(slot.lore().getFirst()));

        int filled = 0;
        int empty = 0;
        for (ResolvedRun run : runs(slot.lore().get(1))) {
            if (run.style().decoration(TextDecoration.STRIKETHROUGH) != TextDecoration.State.TRUE) {
                continue;
            }
            int spaces = Math.toIntExact(run.text().codePoints().filter(codePoint -> codePoint == ' ').count());
            if (run.style().color() == MenuProgressPalette.STANDARD.filled()) {
                filled += spaces;
            }
            if (run.style().color() == NamedTextColor.WHITE) {
                empty += spaces;
            }
            assertEquals(TextDecoration.State.TRUE, run.style().decoration(TextDecoration.BOLD));
        }
        assertEquals(expectedFill, filled);
        assertEquals(25 - expectedFill, empty);
        assertEquals(25, filled + empty);
    }

    private static List<String> lore(MenuDisplayItem item) {
        return lore(HouseMenuCompiler.compile(0, item));
    }

    private static List<String> lore(MenuButton item) {
        return lore(HouseMenuCompiler.compile(0, item));
    }

    private static List<String> lore(MenuSlot slot) {
        return slot.lore().stream().map(ComponentText::flatten).toList();
    }

    private static boolean hasRun(Component component, String text, TextColor color) {
        return runs(component).stream()
                .anyMatch(run -> run.text().contains(text) && run.style().color() == color);
    }

    private static TextColor resolvedColor(Component component) {
        return runs(component).stream()
                .filter(run -> !run.text().isEmpty())
                .findFirst()
                .orElseThrow()
                .style()
                .color();
    }

    private static List<ResolvedRun> runs(Component component) {
        List<ResolvedRun> runs = new ArrayList<>();
        appendRuns(component, Style.empty(), runs);
        return runs;
    }

    private static void appendRuns(Component component, Style inherited, List<ResolvedRun> runs) {
        Style resolved = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            runs.add(new ResolvedRun(text.content(), resolved));
        }
        component.children().forEach(child -> appendRuns(child, resolved, runs));
    }

    private record ResolvedRun(String text, Style style) {
    }
}
