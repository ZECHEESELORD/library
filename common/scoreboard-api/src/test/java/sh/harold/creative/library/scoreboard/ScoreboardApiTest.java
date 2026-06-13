package sh.harold.creative.library.scoreboard;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.tick.InstanceConflictPolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreboardApiTest {

    @Test
    void rejectsBlankSectionIds() {
        assertThrows(IllegalArgumentException.class, () -> ScoreboardSection.fixed("", Component.text("line")));
        assertThrows(IllegalArgumentException.class, () -> ScoreboardSection.fixed(" ", Component.text("line")));
    }

    @Test
    void rejectsDuplicateSectionIds() {
        Key key = Key.key("creative", "board");

        assertThrows(IllegalArgumentException.class, () -> ScoreboardSpec.builder(key)
                .fixedSection("info", Component.text("one"))
                .fixedSection("info", Component.text("two"))
                .build());
    }

    @Test
    void preservesFixedSectionLinesByCopy() {
        List<Component> source = new java.util.ArrayList<>();
        source.add(Component.text("one"));
        ScoreboardSection section = ScoreboardSection.fixed("info", source);
        source.set(0, Component.text("two"));

        assertEquals(List.of(Component.text("one")), section.content().render(context()));
    }

    @Test
    void rejectsFooterThatCannotFitLineBudget() {
        assertThrows(IllegalArgumentException.class, () -> ScoreboardSpec.builder(Key.key("creative", "board"))
                .maxLines(1)
                .footer(Component.text("one"), Component.text("two"))
                .build());
    }

    @Test
    void targetPlacementsRequireTargetSectionId() {
        assertThrows(NullPointerException.class, () -> TransientSectionSpec.builder(Key.key("creative", "flash"))
                .section(ScoreboardSection.fixed("flash", Component.text("line")))
                .placement(TransientPlacement.REPLACE_SECTION)
                .build());
    }

    @Test
    void transientBuilderDefaultsToReplacePolicy() {
        TransientSectionSpec spec = TransientSectionSpec.builder(Key.key("creative", "flash"))
                .section(ScoreboardSection.fixed("flash", Component.text("line")))
                .ttlTicks(5)
                .build();

        assertEquals(TransientPlacement.TOP, spec.placement());
        assertEquals(InstanceConflictPolicy.REPLACE, spec.conflictPolicy());
        assertEquals(5L, spec.ttlTicks());
    }

    private static ScoreboardContext context() {
        return new ScoreboardContext(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Key.key("creative", "board"),
                0L
        );
    }
}
