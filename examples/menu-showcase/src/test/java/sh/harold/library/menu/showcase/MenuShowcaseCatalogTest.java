package sh.harold.library.menu.showcase;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuInteraction;
import sh.harold.library.menu.MenuSlot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuShowcaseCatalogTest {

    @Test
    void catalogContainsTwentyGoldensAndExactlyTenClearlyLabeledSynthesizedExamples() {
        MenuShowcaseCatalog catalog = new MenuShowcaseCatalog();

        assertEquals(20, catalog.goldens().size());
        assertEquals(10, catalog.synthesized().size());
        assertEquals(30, catalog.entries().size());
        assertEquals(Set.of(
                        "Network Browser",
                        "Guild Operations",
                        "Loadout Workshop",
                        "Upgrade Confirmation",
                        "Quest Journal",
                        "Match Finder",
                        "Tournament Control",
                        "Forge Queue",
                        "Salvage Station",
                        "Mail Locker"),
                catalog.synthesized().stream().map(ShowcaseEntry::label).collect(java.util.stream.Collectors.toSet()));
        assertTrue(catalog.goldens().stream().allMatch(entry -> entry.source().isPresent()));
        assertTrue(catalog.goldens().stream()
                .flatMap(entry -> entry.source().orElseThrow().items().stream())
                .allMatch(item -> item.sha256().length() == 64));
        assertTrue(catalog.synthesized().stream().allMatch(entry -> entry.snapshots().size() == 2));
    }

    @Test
    void synthesizedExamplesCollectivelyExerciseTheCompleteV9FeatureMatrix() {
        MenuShowcaseCatalog catalog = new MenuShowcaseCatalog();
        EnumSet<ShowcaseFeature> covered = EnumSet.noneOf(ShowcaseFeature.class);
        catalog.synthesized().forEach(entry -> covered.addAll(entry.features()));

        assertEquals(EnumSet.allOf(ShowcaseFeature.class), covered);
    }

    @Test
    void everyGoldenAndSynthesizedStateMatchesTheStyleAwareSnapshot() throws IOException {
        String actual = snapshot(new MenuShowcaseCatalog());
        Path generated = Path.of("build", "generated-snapshots", "menu-showcase.snap");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, actual, StandardCharsets.UTF_8);

        try (InputStream input = getClass().getResourceAsStream("/menu-showcase.snap")) {
            assertNotNull(input, "Missing menu-showcase.snap; generated candidate at " + generated.toAbsolutePath());
            String expected = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(expected, actual);
        }
    }

    private static String snapshot(MenuShowcaseCatalog catalog) {
        StringBuilder output = new StringBuilder();
        for (ShowcaseEntry entry : catalog.entries()) {
            output.append("ENTRY ").append(entry.id())
                    .append(" | ").append(entry.origin())
                    .append(" | ").append(entry.label()).append('\n');
            entry.source().ifPresent(source -> {
                output.append("SOURCE surface=").append(source.surfaceSha256()).append('\n');
                source.items().forEach(item -> output.append("SOURCE_ITEM slot=")
                        .append(item.slot()).append(" sha256=").append(item.sha256()).append('\n'));
            });
            output.append("FEATURES ").append(new TreeSet<>(entry.features())).append('\n');
            for (ShowcaseSnapshot state : entry.snapshots()) {
                output.append("STATE ").append(state.state()).append('\n');
                appendMenu(output, state.menu());
            }
            output.append("END_ENTRY\n\n");
        }
        return output.toString();
    }

    private static void appendMenu(StringBuilder output, Menu menu) {
        output.append("GEOMETRY ").append(menu.geometry())
                .append(" rows=").append(menu.rows())
                .append(" initial=").append(menu.initialFrameId()).append('\n');
        menu.frameIds().stream().sorted().forEach(frameId -> {
            MenuFrame frame = menu.frame(frameId);
            output.append("FRAME ").append(frameId)
                    .append(" title=").append(component(frame.title())).append('\n');
            frame.slots().stream()
                    .filter(slot -> !isFiller(slot))
                    .forEach(slot -> appendSlot(output, slot));
        });
    }

    private static void appendSlot(StringBuilder output, MenuSlot slot) {
        output.append("SLOT ").append(slot.slot())
                .append(" icon=").append(slot.icon().key())
                .append(" texture=").append(slot.icon().textureValue() == null ? "-" : slot.icon().textureValue())
                .append(" glow=").append(slot.glow())
                .append(" amount=").append(slot.amount())
                .append(" tooltip=").append(slot.tooltipBehavior())
                .append(" replaceable=").append(slot.replaceableLoreLineCount())
                .append('\n');
        output.append("  TITLE ").append(component(slot.title())).append('\n');
        for (int index = 0; index < slot.lore().size(); index++) {
            Component line = slot.lore().get(index);
            output.append("  LORE[").append(index).append("] ")
                    .append(flatten(line).isEmpty() ? "<blank>" : component(line))
                    .append('\n');
        }
        slot.interactions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Enum::ordinal)))
                .forEach(entry -> appendInteraction(output, entry.getKey(), entry.getValue()));
    }

    private static void appendInteraction(
            StringBuilder output,
            MenuClick click,
            MenuInteraction interaction
    ) {
        output.append("  ACTION ").append(click)
                .append(" verb=").append(interaction.verb())
                .append(" prompt=").append(escape(interaction.promptLabel()))
                .append(" type=").append(interaction.action().getClass().getSimpleName())
                .append(" sound=").append(interaction.soundCueKey() == null ? "-" : interaction.soundCueKey())
                .append('\n');
    }

    private static boolean isFiller(MenuSlot slot) {
        return slot.icon().key().equals("minecraft:black_stained_glass_pane")
                && flatten(slot.title()).isBlank()
                && slot.lore().isEmpty()
                && slot.interactions().isEmpty();
    }

    private static String component(Component component) {
        List<Run> runs = new ArrayList<>();
        appendRuns(component, Style.empty(), runs);
        if (runs.isEmpty()) {
            return "<blank>";
        }
        StringBuilder output = new StringBuilder();
        for (Run run : runs) {
            if (!output.isEmpty()) {
                output.append(" + ");
            }
            Style style = run.style();
            output.append('{')
                    .append("text=\"").append(escape(run.text())).append('\"')
                    .append(",color=").append(style.color() == null
                            ? "-"
                            : String.format("#%06x", style.color().value()))
                    .append(",bold=").append(style.decoration(TextDecoration.BOLD))
                    .append(",italic=").append(style.decoration(TextDecoration.ITALIC))
                    .append(",strike=").append(style.decoration(TextDecoration.STRIKETHROUGH))
                    .append('}');
        }
        return output.toString();
    }

    private static void appendRuns(Component component, Style inherited, List<Run> runs) {
        Style resolved = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            runs.add(new Run(text.content(), resolved));
        }
        component.children().forEach(child -> appendRuns(child, resolved, runs));
    }

    private static String flatten(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof TextComponent value) {
            text.append(value.content());
        }
        component.children().forEach(child -> text.append(flatten(child)));
        return text.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\"", "\\\"");
    }

    private record Run(String text, Style style) {
    }
}
